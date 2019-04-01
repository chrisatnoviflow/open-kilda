package org.openkilda.functionaltests.spec.stats


import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.tools.FlowTrafficExamBuilder

import groovy.time.TimeCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Narrative("TBD")
class MflStatSpec extends BaseSpecification {
    @Shared
    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    @Value('${floodlight.controller.management}')
    private String managementController

    @Value('${floodlight.controller.stat}')
    private String statController

    @Autowired
    Provider<TraffExamService> traffExamProvider

    def "System collects stat from the stat controller only"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeTraffGens*.switchConnected
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 100
        flowHelper.addFlow(flow)

        when: "Generate traffic on the given flow"
        def traffExam = traffExamProvider.get()
        def exam = new FlowTrafficExamBuilder(topology, traffExam).buildExam(flow, (int) flow.maximumBandwidth)
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Init stat in openTSDB is created"
        Date startTime = use(TimeCategory) { new Date() - 20.minutes }
        def metric = metricPrefix + "flow.bytes"
        def tag = [flowid: flow.id]
        def waitTime = 60
        def initStat
        Wrappers.wait(waitTime) {
            initStat = otsdb.query(startTime, metric, tag).dps.size()
            assert initStat == 1
        }

        when: "Set management controller on switches which are affected by the flow"
        def currentPath = pathHelper.convert(northbound.getFlowPath(flow.id))*.switchId
        currentPath.each { lockKeeper.setController(it, managementController) }

        and: "Generate traffic on the given flow"
        exam.setResources(traffExam.startExam(exam, true))
        assert traffExam.waitExam(exam).hasTraffic()

        then: "Statistics should not be collected because stats controller was removed"
        double interval = waitTime * 0.2
        def statAfterRemovingStatContr
        Wrappers.timedLoop(waitTime) {
            statAfterRemovingStatContr = otsdb.query(startTime, metric, tag).dps.size()
            statAfterRemovingStatContr == initStat
            sleep((interval * 1000).toLong())
        }

        when: "Restore default controllers on updated switches"
        currentPath.each { lockKeeper.setController(it, managementController + " " + statController) }

        then: "Old statistic should be collected"
        Wrappers.wait(waitTime) {
            def lastStat = otsdb.query(startTime, metric, tag).dps.size()
            lastStat > statAfterRemovingStatContr
        }
    }
}
