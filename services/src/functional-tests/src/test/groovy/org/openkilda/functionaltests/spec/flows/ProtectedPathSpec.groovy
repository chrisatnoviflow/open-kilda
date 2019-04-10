package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.Cookie
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("TBD")
class ProtectedPathSpec extends BaseSpecification {
    def "Able to create flow with the 'protected path' option"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        northbound.getFlowPath(flow.id).protectedPath

        and: "Rules for protected path are created"
        Wrappers.wait(WAIT_OFFSET) {
            [srcSwitch, dstSwitch].each {
                def rules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
                    !Cookie.isDefaultRule(it.cookie)
                }
                /** 2 - egress, 1 - ingress
                 * protected path creates an egress rule only*/
                assert rules.size() == 3
                assert rules.findAll { it.flags.contains("RESET_COUNTS") }.size() == 1
                assert rules.findAll { !it.flags.contains("RESET_COUNTS") }.size() == 2
            }
        }

        pathHelper.getInvolvedSwitches(flow.id)[1..-2].each { sw ->
            def rules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }
            assert rules.size() == 2
            assert rules.findAll { !it.flags.contains("RESET_COUNTS") }.size() == 2
        }

        pathHelper.getInvolvedSwitchesForProtectedPath(flow.id)[1..-2].each { sw ->
            def rules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }
            assert rules.size() == 2
            assert rules.findAll { !it.flags.contains("RESET_COUNTS") }.size() == 2
        }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Able to update the 'protected path' option"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = false
        flowHelper.addFlow(flow)

        then: "Flow is created without protected path"
        !northbound.getFlowPath(flow.id).protectedPath

        and: "Needed number of rules are created"
        northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll { !Cookie.isDefaultRule(it.cookie) }.size() == 2

        pathHelper.getInvolvedSwitches(flow.id)[1..-1].each { sw ->
            assert northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }.size() == 2
        }

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Protected path is enabled"
        northbound.getFlowPath(flow.id).protectedPath

        and: "Rules are updated  on the main path"
        Wrappers.wait(WAIT_OFFSET) {
            [srcSwitch, dstSwitch].each {
                def rules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
                    !Cookie.isDefaultRule(it.cookie)
                }
                /** 2 - egress, 1 - ingress
                 * protected path creates an egress rule only*/
                assert rules.size() == 3
                assert rules.findAll { it.flags.contains("RESET_COUNTS") }.size() == 1
                assert rules.findAll { !it.flags.contains("RESET_COUNTS") }.size() == 2
            }
        }

        pathHelper.getInvolvedSwitches(flow.id)[1..-2].each { sw ->
            def rules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }
            assert rules.size() == 2
            assert rules.findAll { !it.flags.contains("RESET_COUNTS") }.size() == 2
        }

        and: "Rules updated on the protected path"
        def involvedSwitchesProtectedPath = pathHelper.getInvolvedSwitchesForProtectedPath(flow.id)[1..-2]
        involvedSwitchesProtectedPath.each { sw ->
            def rules = northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }
            assert rules.size() == 2
            assert rules.findAll { !it.flags.contains("RESET_COUNTS") }.size() == 2
        }

        when: "Update flow: disable protected path(allocateProtectedPath=false)"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = false })

        then: "Protected path is disabled"
        !northbound.getFlowPath(flow.id).protectedPath

        and: "Rules for protected path are deleted"
        Wrappers.wait(WAIT_OFFSET) {
            [srcSwitch, dstSwitch].each {
                def rules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
                    !Cookie.isDefaultRule(it.cookie)
                }
                assert rules.size() == 2
                assert rules.findAll { it.flags.contains("RESET_COUNTS") }.size() == 1
                assert rules.findAll { !it.flags.contains("RESET_COUNTS") }.size() == 1
            }
        }

        involvedSwitchesProtectedPath.each { sw ->
            assert northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }.size() == 0
        }

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Unable to create flow with the 'protected path' option on a single switch flow"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        when: "Create single switch flow"
        def flow = flowHelper.singleSwitchFlow(sw)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

//       ("HttpServerErrorException -> HttpClientErrorException") 500 -> 400
        then: "Human readable error is returned"
//        def exc = thrown(HttpClientErrorException)
//        exc.rawStatusCode == 400
        def exc = thrown(HttpServerErrorException)
        exc.rawStatusCode == 500
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Couldn't setup protected path for one-switch flow"
    }

    def "Unable to update flow with the 'protected path' option on a single switch flow"() {
        given: "A switch"
        def sw = topology.activeSwitches.first()

        and: "A flow without protected path"
        def flow = flowHelper.singleSwitchFlow(sw)
        flowHelper.addFlow(flow)

        when: "Update flow: enable protected path"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpServerErrorException)
        exc.rawStatusCode == 500
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not update flow: Couldn't setup protected path for one-switch flow"
    }

    @Unroll
    def "System is able to reroute #flowDescription flow to protected path"() {
        given: "All Isls"
        def allIsls = northbound.getAllLinks()

        and: "Source and destination switches"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allIsls.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create flow with protected bandwidth"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        northbound.getFlowPath(flow.id).protectedPath
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Current path is not equal to protected path"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def currentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        currentPath != currentProtectedPath

        and: "Bandwidth is reserved for protected path on involved isls"
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def protectedIslsInfo = protectedIsls.collect { islUtils.getIslInfo(it).get() }

        allIsls.each { originIsl ->
            protectedIslsInfo.each { newIsl ->
                if (originIsl.id == newIsl.id) {
                    assert originIsl.availableBandwidth - newIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        when: "Init reroute"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def islToBreak = currentIsls.find { !protectedIsls.contains(it) }
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "Flow is rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert pathHelper.convert(northbound.getFlowPath(flow.id)) != currentPath
        }

        and: "Current path is protected path"
        pathHelper.convert(northbound.getFlowPath(flow.id)) == currentProtectedPath

        when: "Restore port status"
        northbound.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }

        then: "Path of the flow is not changed"
        pathHelper.convert(northbound.getFlowPath(flow.id)) == currentProtectedPath

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
        database.resetCosts()
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }

        where:
        flowDescription | bandwidth
        "metered"       | 1000
        "unmetered"     | 0
    }

    def "Unable to create a flow with the the 'protected path' option in case is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update available bandwidth on all isls except first"
        def bandwidth = 100
        isls[1..-1].each { database.updateIslAvailableBandwidth(it, 90) }

        when: "Create flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        // TODO 404 ?
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found : Couldn't find non overlapping protected path"

        and: "Cleanup: delete the flow and restore available bandwidth"
        isls.each { database.resetIslBandwidth(it) }
    }

    def "Unable to update the 'protected path' option on a flow in case is not enough bandwidth"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update available bandwidth on all isls except first"
        def bandwidth = 100
        isls[1..-1].each { database.updateIslAvailableBandwidth(it, 90) }

        when: "Create flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = false
        flowHelper.addFlow(flow)

        then: "Flow is created without protected path"
        !northbound.getFlowPath(flow.id).protectedPath

        when: "Update flow: enable protected path"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        // TODO 404 ?
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not update flow: Not enough bandwidth found or path not found"

        and: "Cleanup: delete the flow and restore available bandwidth"
        isls.each { database.resetIslBandwidth(it) }
    }

    def "Able to create a flow with 'protected path' in case is not enough bandwidth and ignoreBandwidth=true"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update available bandwidth on all isls except first"
        def bandwidth = 100
        isls[1..-1].each { database.updateIslAvailableBandwidth(it, 90) }

        when: "Create flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = true
        flow.ignoreBandwidth = true
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        northbound.getFlowPath(flow.id).protectedPath

        and: "Cleanup: delete the flow and restore available bandwidth"
        flowHelper.deleteFlow(flow.id)
        isls.each { database.resetIslBandwidth(it) }
    }

    @Ignore("Test is not valid, the availableBandwidth field is rewritten by each update action")
    def "Able to enable protected path on a flow in case is not enough bandwidth for protectedPath and ignoreBandwidth=true"() {
        given: "Two active neighboring switches"
        def isls = topology.getIslsForActiveSwitches()
        def (srcSwitch, dstSwitch) = [isls.first().srcSwitch, isls.first().dstSwitch]

        and: "Update available bandwidth on all isls except first"
        def bandwidth = 100
        isls[1..-1].each { isl ->
            database.updateIslAvailableBandwidth(isl, 90)
            database.updateIslAvailableBandwidth(isl.reversed, 90)
        }

        when: "Create flow without protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = bandwidth
        flow.allocateProtectedPath = false
        flow.ignoreBandwidth = true
        flowHelper.addFlow(flow)

        then: "Flow is created without protected path"
        !northbound.getFlowPath(flow.id).protectedPath

        when: "Update flow: enable protected path(allocateProtectedPath=true)"
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        then: "Protected path is enabled"
        northbound.getFlowPath(flow.id).protectedPath

        // TODO I've second thoughts about the following lines
        northbound.updateFlow(flow.id, flow.tap { it.ignoreBandwidth = false })
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = false })
        //Should the protected path be enabled?
//        isls[1..-1].each { isl ->
//            database.updateIslAvailableBandwidth(isl, 90)
//            database.updateIslAvailableBandwidth(isl.reversed, 90)
//        }
        northbound.updateFlow(flow.id, flow.tap { it.allocateProtectedPath = true })

        and: "Cleanup: delete the flow and restore available bandwidth"
        flowHelper.deleteFlow(flow.id)
        isls.each { isl ->
            database.resetIslBandwidth(isl)
            database.resetIslBandwidth(isl.reversed)
        }
    }

    def "System is able to recalculate protected path when port is down"() {
        given: "All Isls"
        def allIsls = northbound.getAllLinks()

        and: "Source and destination switches"
        def switches = topology.getActiveSwitches()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allIsls.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue("No suiting switches found", false)

        when: "Create a flow with protected bandwidth"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        then: "Flow is created with protected path"
        northbound.getFlowPath(flow.id).protectedPath
        Wrappers.wait(WAIT_OFFSET) { northbound.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Current path is not equal to protected path"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))
        def currentProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
        currentPath != currentProtectedPath

        and: "Bandwidth is reserved for protected path on involved isls"
        def protectedIsls = pathHelper.getInvolvedIsls(currentProtectedPath)
        def protectedIslsInfo = protectedIsls.collect { islUtils.getIslInfo(it).get() }

        allIsls.each { originIsl ->
            protectedIslsInfo.each { newIsl ->
                if (originIsl.id == newIsl.id) {
                    assert originIsl.availableBandwidth - newIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        when: "Switch some port to DOWN state on the protected path to init the recalculate procedure"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def islToBreakProtectedPath = protectedIsls.find { !currentIsls.contains(it) }
        northbound.portDown(islToBreakProtectedPath.dstSwitch.dpId, islToBreakProtectedPath.dstPort)

        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreakProtectedPath).get().state == IslChangeType.FAILED
        }

        then: "Protected path is recalculated"
        def newProtectedPath
        Wrappers.wait(WAIT_OFFSET) {
            newProtectedPath = pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath)
            newProtectedPath != currentProtectedPath
        }

        and: "Bandwidth is reserved for new protected path on involved isls"
        def newProtectedIsls = pathHelper.getInvolvedIsls(newProtectedPath)
        def newProtectedIslsInfo = newProtectedIsls.collect { islUtils.getIslInfo(it).get() }

        allIsls.each { originIsl ->
            newProtectedIslsInfo.each { newIsl ->
                if (originIsl.id == newIsl.id) {
                    assert originIsl.availableBandwidth - newIsl.availableBandwidth == flow.maximumBandwidth
                }
            }
        }

        and: "Reservation is deleted on the broken isl"
        def originInfoBrokenIsl = allIsls.find {
            it.destination.switchId == islToBreakProtectedPath.dstSwitch.dpId &&
                    it.destination.portNo == islToBreakProtectedPath.dstPort
        }
        def currentInfoBrokenIsl = islUtils.getIslInfo(islToBreakProtectedPath).get()

        originInfoBrokenIsl.availableBandwidth == currentInfoBrokenIsl.availableBandwidth

        when: "Restore port status"
        northbound.portUp(islToBreakProtectedPath.dstSwitch.dpId, islToBreakProtectedPath.dstPort)
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreakProtectedPath).get().state == IslChangeType.DISCOVERED
        }

        then: "Path is not recalculated again"
        pathHelper.convert(northbound.getFlowPath(flow.id).protectedPath) == newProtectedPath

        and: "Cleanup: revert system to original state"
        flowHelper.deleteFlow(flow.id)
        database.resetCosts()
    }

//    test new API swap, no any mention about it in ticket
//    update chaosSpec
//    test isl/switch maintenance
//    run tests related to the validateRule action
//    run tests related to the synchronize action
//    error message/code will be fixed
//    port anti flap ??
}

