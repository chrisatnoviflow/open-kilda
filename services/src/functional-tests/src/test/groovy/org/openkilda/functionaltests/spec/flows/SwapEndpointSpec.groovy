package org.openkilda.functionaltests.spec.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.payload.flow.SwapFlowPayload

class SwapEndpointSpec extends BaseSpecification {

    def "Able to swap endpoints for flows with the same source and destination switches"() {
        given: "Two flows with the same source and destination switches"
        def switchPair = topologyHelper.getNotNeighboringSwitchPair()
        def flow1 = flowHelper.randomFlow(switchPair)
        def flow2 = flowHelper.randomFlow(switchPair, false, [flow1])

        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap source endpoints for flows"
        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flow2.source, flow1.destination),
                new SwapFlowPayload(flow2.id, flow1.source, flow2.destination))
        def flow1Updated = northbound.getFlow(flow1.id)
        def flow2Updated = northbound.getFlow(flow2.id)

        then: "Endpoints are successfully swapped"
        response.firstFlow.source == flow2.source
        response.firstFlow.destination == flow1.destination

        response.secondFlow.source == flow1.source
        response.secondFlow.destination == flow2.destination

        flow1Updated.source == flow2.source
        flow1Updated.destination == flow1.destination

        flow2Updated.source == flow1.source
        flow2Updated.destination == flow2.destination

        when: "Try to swap destination endpoints for flows"
        response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flow2.source, flow2.destination),
                new SwapFlowPayload(flow2.id, flow1.source, flow1.destination))
        flow1Updated = northbound.getFlow(flow1.id)
        flow2Updated = northbound.getFlow(flow2.id)

        then: "Endpoints are successfully swapped"
        response.firstFlow.source == flow2.source
        response.firstFlow.destination == flow2.destination

        response.secondFlow.source == flow1.source
        response.secondFlow.destination == flow1.destination

        flow1Updated.source == flow2.source
        flow1Updated.destination == flow2.destination

        flow2Updated.source == flow1.source
        flow2Updated.destination == flow1.destination

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
    }
}
