package org.openkilda.functionaltests.spec.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.messaging.payload.flow.SwapFlowPayload

import spock.lang.Unroll

class SwapEndpointSpec extends BaseSpecification {

    @Unroll
    def "Able to swap endpoints for flows with the same source and destination switches"() {
        given: "Two flows with the same source and destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap endpoints for flows"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.id, flow2Src, flow2Dst))

        def flow1Updated = northbound.getFlow(flow1.id)
        def flow2Updated = northbound.getFlow(flow2.id)

        then: "Endpoints are successfully swapped"
        response.firstFlow.source == flow1Src
        response.firstFlow.destination == flow1Dst
        response.secondFlow.source == flow2Src
        response.secondFlow.destination == flow2Dst

        flow1Updated.source == flow1Src
        flow1Updated.destination == flow1Dst
        flow2Updated.source == flow2Src
        flow2Updated.destination == flow2Dst

        (flow1Path == PathHelper.convert(northbound.getFlowPath(flow1.id))) == pathsAreEqual
        (flow2Path == PathHelper.convert(northbound.getFlowPath(flow2.id))) == pathsAreEqual

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        switchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow1 << [getFlowHelper().randomFlow(switchPair)] * 4
        flow2 << [getFlowHelper().randomFlow(switchPair, false, [flow1])] * 4
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
        pathsAreEqual << [true, true, false, false]
    }
}
