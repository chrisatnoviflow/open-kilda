package org.openkilda.functionaltests.spec.flows


import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.payload.flow.SwapFlowEndpointPayload

import groovy.util.logging.Slf4j
import spock.lang.Narrative

@Slf4j
@Narrative("""TBD""")
class SwapEndpointSpec extends BaseSpecification {
    def "Swap endpoints when flow is single switch flow"() {
        given: "Two single switch flow on the same switch"
        def sw = topology.activeSwitches.first()
        def flow1 = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def flow2 = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))

        when: "Try to swap endpoints on the given flows"
        northboundV2.swapFlowEndpoint(new SwapFlowEndpointPayload(flow1, flow2))
        then: "Endpoints ?(is/is not) swapped"
//      ??? swapped or not swapped
//        and: "Cleanup: Delete the flows"
        [flow1, flow2].each { flow -> flowHelper.deleteFlow(flow.id) }
    }
    // swap endpoint for a simple flow(Two single switch flow on the same switch)
    // swap endpoint for a simple flow(Two single switch flow on the different switches)
    // swap endpoint for a single switch flow
    //swap when one switch is under maintenance
    // swap when one flow is INACTIVE
    // swap when one flow is protected flow/ what will happen with protected path?
    // swap flow with different bandwidth
    // swap flow when not enough bandwidth
    // swap flow when not enough bandwidth and ignoreBandwidth = true
}
