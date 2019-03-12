package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Memoized
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("""This test suite check the meter validate request.
description of fields:
- missing - those meters, which is NOT exist on a switch, but it is exist in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters, which is exist on a switch, but it is NOT exist in db
- proper - meters value is the same on a switch and in db
""")
class MetersValidateSpec extends BaseSpecification {
    @Unroll
    def "Able to get meter validate information"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)

        then: "Two meters are automatically created."
        createdMetersId.size() == 2

        and: "The correct info is stored in the validate meter response"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        meterValidateInfo.meters.proper.collect { it.meterId }.containsAll(createdMetersId)
        checkNoMeterValidation(sw.dpId, "proper")

        and: "Created rules are stored in the proper section"
//        def createdCookies = northbound.getSwitchRules(sw.dpId).flowEntries*.instructions.findAll {it.goToMeter in createdMetersId}
        def createdCookies = getCreatedCookies(sw.dpId, createdMetersId)
        meterValidateInfo.rules.properRules.containsAll(createdCookies)

        then: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check that meter validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)

        where:
        switchType   | switches
//        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    def "Able to move meter info into the misconfigured section"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)

        and: "Change bandwidth for the created flow directly in DB"
        def newBandwidth = northbound.getSwitchRules(sw.dpId).flowEntries*.instructions.goToMeter.max() + 100
        /** at this point meters are set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meters from the proper section into the misconfigured*/
        database.updateFlowBandwidth(flow.id, newBandwidth)
        //at this point existing meters do not correspond with the flow

        then: "Meters info are moved into the misconfigured section"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        meterValidateInfo.meters.misconfigured.collect { it.meterId }.containsAll(createdMetersId)

        and: "Reason is specified why meters are misconfigured"
        meterValidateInfo.meters.misconfigured.each {
            it.actual.rate == flow.maximumBandwidth
            it.expected.rate == newBandwidth
        }

        and: "The rest fields are empty"
        checkNoMeterValidation(sw.dpId, "misconfigured")

        and: "Created rules are still stored in the proper section"
        def createdCookies = getCreatedCookies(sw.dpId, createdMetersId)
        meterValidateInfo.rules.properRules.containsAll(createdCookies)

        when: "Restore correct bandwidth via DB"
        database.updateFlowBandwidth(flow.id, flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the proper section"
        def meterValidateInfoRestored = northbound.validateMeters(sw.dpId)
        meterValidateInfoRestored.meters.proper.collect { it.meterId }.containsAll(createdMetersId)
        checkNoMeterValidation(sw.dpId, "proper")

        then: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check that meter validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)

        where:
        switchType   | switches
//        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    def "Able to move meter info into the missing section"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)

        and: "Remove created meter"
        northbound.deleteMeter(sw.dpId, createdMetersId[0])
        northbound.deleteMeter(sw.dpId, createdMetersId[1])

        then: "Meters info/rules are moved into the missing section"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        meterValidateInfo.meters.missing.collect { it.meterId }.containsAll(createdMetersId)
        meterValidateInfo.meters.missing.each { assert it.flowId == flow.id }

        and: "The rest sections are empty"
        checkNoMeterValidation(sw.dpId, "missing")
        checkNoRuleMeterValidation(sw.dpId, "missingRules")

        then: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check that meter validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)


        where:
        switchType   | switches
//        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    def "Able to move meter info into the excess section"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)

        and: "Update meterId for created flow directly via db"
        def newMeterId = 100
        database.updateFlowMeterId(flow.id, newMeterId)

        then: "Meters info are moved into the excess section"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        meterValidateInfo.meters.excess.collect { it }.containsAll(createdMetersId)
        meterValidateInfo.meters.missing.each {
            assert it.flowId == flow.id
            assert it.meterId == newMeterId
        }

        and: "Rules are still exist in the proper section"
        checkNoRuleMeterValidation(sw.dpId, "properRules")

        then: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check that meter validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)

        where:
        switchType   | switches
//        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Memoized
    List<Switch> getNonCentecSwitches() {
        topology.activeSwitches.findAll { !it.centec && it.ofVersion == "OF_13" }
    }

    @Memoized
    List<Switch> getCentecSwitches() {
        topology.getActiveSwitches().findAll { it.centec }
    }

    List<Integer> getCreatedMetersId(switchId, defaultMeters) {
        northbound.getAllMeters(switchId).meterEntries.findAll {
            !defaultMeters.meterEntries*.meterId.contains(it.meterId)
        }.collect { it.meterId }
    }

    List<Integer> getCreatedCookies(switchId, createdMetersId) {
        def createdCookies = []
        createdMetersId.each { id ->
            northbound.getSwitchRules(switchId).flowEntries.each {
                if (it.instructions.goToMeter == id) {
                    createdCookies << it.cookie
                }
            }
        }
        return createdCookies
    }

    void checkNoMeterValidation(switchId, String excludeSection = null) {
        def listOfSections = ["missing", "misconfigured", "proper", "excess"]
        listOfSections.remove(excludeSection)

        listOfSections.each {
            assert northbound.validateMeters(switchId).meters."$it".size() == 0
        }
    }

    void checkNoRuleMeterValidation(switchId, String excludeSection = null) {
        def listOfSections = ["missingRules", "properRules", "excessRules"]
        listOfSections.remove(excludeSection)

        listOfSections.each {
            assert northbound.validateMeters(switchId).rules."$it".size() == 0
        }
    }
}
