package org.openkilda.functionaltests.spec.flows

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.SwapFlowPayload

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Unroll

class SwapEndpointSpec extends BaseSpecification {

    @Unroll
    def "Able to swap #endpointsPart (#description) for two flows with the same source and destination switches"() {
        given: "Two flows with the same source and destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap #endpointsPart for flows"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.id, flow2Src, flow2Dst))

        def flow1Updated = northbound.getFlow(flow1.id)
        def flow2Updated = northbound.getFlow(flow2.id)

        then: "#endpointsPart.capitalize() are successfully swapped"
        response.firstFlow.source == flow1Src
        response.firstFlow.destination == flow1Dst
        response.secondFlow.source == flow2Src
        response.secondFlow.destination == flow2Dst

        flow1Updated.source == flow1Src
        flow1Updated.destination == flow1Dst
        flow2Updated.source == flow2Src
        flow2Updated.destination == flow2Dst

        (flow1Path == PathHelper.convert(northbound.getFlowPath(flow1.id))) == pathNotChanged
        (flow2Path == PathHelper.convert(northbound.getFlowPath(flow2.id))) == pathNotChanged

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        endpointsPart << ["vlans"] * 4 + ["ports"] * 4 + ["switches"] * 4
        description << ["src <-> src", "dst <-> dst", "src <-> dst", "dst <-> src"] * 3
        switchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 12
        flow1 << [getFlowHelper().randomFlow(switchPair)] * 12
        flow2 << [getFlowHelper().randomFlow(switchPair, false, [flow1])] * 12
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(flow1.source, "vlanId", flow2.source.vlanId), flow1.destination,
                 changePropertyValue(flow2.source, "vlanId", flow1.source.vlanId), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "vlanId", flow2.destination.vlanId),
                 flow2.source, changePropertyValue(flow2.destination, "vlanId", flow1.destination.vlanId)],
                [changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)],
                [flow1.source, changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId),
                 changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId), flow2.destination],

                [changePropertyValue(flow1.source, "portNumber", flow2.source.portNumber), flow1.destination,
                 changePropertyValue(flow2.source, "portNumber", flow1.source.portNumber), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.destination.portNumber),
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.destination.portNumber)],
                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber), flow2.destination],

                [changePropertyValue(flow1.source, "datapath", flow2.source.datapath), flow1.destination,
                 changePropertyValue(flow2.source, "datapath", flow1.source.datapath), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "datapath", flow2.destination.datapath),
                 flow2.source, changePropertyValue(flow2.destination, "datapath", flow1.destination.datapath)],
                [changePropertyValue(flow1.source, "datapath", flow2.destination.datapath), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "datapath", flow1.source.datapath)],
                [flow1.source, changePropertyValue(flow1.destination, "datapath", flow2.source.datapath),
                 changePropertyValue(flow2.source, "datapath", flow1.destination.datapath), flow2.destination],
        ]
        pathNotChanged << [true] * 10 + [false, false]
    }

    @Unroll
    def "Able to swap endpoints (#description) for two flows with the same source and destination switches"() {
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

        (flow1Path == PathHelper.convert(northbound.getFlowPath(flow1.id))) == pathNotChanged
        (flow2Path == PathHelper.convert(northbound.getFlowPath(flow2.id))) == pathNotChanged

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["src <-> src", "dst <-> dst", "src <-> dst", "dst <-> src"]
        switchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow1 << [getFlowHelper().randomFlow(switchPair)] * 4
        flow2 << [getFlowHelper().randomFlow(switchPair, false, [flow1])] * 4
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
        pathNotChanged << [true, true, false, false]
    }

    @Unroll
    def "Able to swap #endpointsPart (#description) for two flows with the same source and different destination \
switches"() {
        given: "Two flows with the same source and different destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap #endpointsPart for flows"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.id, flow2Src, flow2Dst))

        def flow1Updated = northbound.getFlow(flow1.id)
        def flow2Updated = northbound.getFlow(flow2.id)

        then: "#endpointsPart.capitalize() are successfully swapped"
        response.firstFlow.source == flow1Src
        response.firstFlow.destination == flow1Dst
        response.secondFlow.source == flow2Src
        response.secondFlow.destination == flow2Dst

        flow1Updated.source == flow1Src
        flow1Updated.destination == flow1Dst
        flow2Updated.source == flow2Src
        flow2Updated.destination == flow2Dst

        (flow1Path == PathHelper.convert(northbound.getFlowPath(flow1.id))) == pathNotChanged
        (flow2Path == PathHelper.convert(northbound.getFlowPath(flow2.id))) == pathNotChanged

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        endpointsPart << ["vlans"] * 4 + ["ports"] * 4 + ["switches"] * 4
        description << ["src <-> src", "dst <-> dst", "src <-> dst", "dst <-> src"] * 3
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 12
        flow2SwitchPair << [getHalfDifferentNotNeighboringSwitchPair(flow1SwitchPair, "src", "dst")] * 12
        flow1 << [getFlowHelper().randomFlow(flow1SwitchPair)] * 12
        flow2 << [getFlowHelper().randomFlow(flow2SwitchPair, false, [flow1])] * 12
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(flow1.source, "vlanId", flow2.source.vlanId), flow1.destination,
                 changePropertyValue(flow2.source, "vlanId", flow1.source.vlanId), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "vlanId", flow2.destination.vlanId),
                 flow2.source, changePropertyValue(flow2.destination, "vlanId", flow1.destination.vlanId)],
                [changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)],
                [flow1.source, changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId),
                 changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId), flow2.destination],

                [changePropertyValue(flow1.source, "portNumber", flow2.source.portNumber), flow1.destination,
                 changePropertyValue(flow2.source, "portNumber", flow1.source.portNumber), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.destination.portNumber),
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.destination.portNumber)],
                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber), flow2.destination],

                [changePropertyValue(flow1.source, "datapath", flow2.source.datapath), flow1.destination,
                 changePropertyValue(flow2.source, "datapath", flow1.source.datapath), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "datapath", flow2.destination.datapath),
                 flow2.source, changePropertyValue(flow2.destination, "datapath", flow1.destination.datapath)],
                [changePropertyValue(flow1.source, "datapath", flow2.destination.datapath), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "datapath", flow1.source.datapath)],
                [flow1.source, changePropertyValue(flow1.destination, "datapath", flow2.source.datapath),
                 changePropertyValue(flow2.source, "datapath", flow1.destination.datapath), flow2.destination],
        ]
        pathNotChanged << [true] * 9 + [false, false, false]
    }

    @Unroll
    def "Able to swap endpoints (#description) for two flows with the same source and different destination \
switches"() {
        given: "Two flows with the same source and different destination switches"
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

        (flow1Path == PathHelper.convert(northbound.getFlowPath(flow1.id))) == pathNotChanged
        (flow2Path == PathHelper.convert(northbound.getFlowPath(flow2.id))) == pathNotChanged

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["src <-> src", "dst <-> dst", "src <-> dst", "dst <-> src"]
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow2SwitchPair << [getHalfDifferentNotNeighboringSwitchPair(flow1SwitchPair, "src", "dst")] * 4
        flow1 << [getFlowHelper().randomFlow(flow1SwitchPair)] * 4
        flow2 << [getFlowHelper().randomFlow(flow2SwitchPair, false, [flow1])] * 4
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
        pathNotChanged << [true, false, false, false]
    }

    @Unroll
    def "Able to swap #endpointsPart (#description) for two flows with different source and the same destination \
switches"() {
        given: "Two flows with different source and the same destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap #endpointsPart for flows"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.id, flow2Src, flow2Dst))

        def flow1Updated = northbound.getFlow(flow1.id)
        def flow2Updated = northbound.getFlow(flow2.id)

        then: "#endpointsPart.capitalize() are successfully swapped"
        response.firstFlow.source == flow1Src
        response.firstFlow.destination == flow1Dst
        response.secondFlow.source == flow2Src
        response.secondFlow.destination == flow2Dst

        flow1Updated.source == flow1Src
        flow1Updated.destination == flow1Dst
        flow2Updated.source == flow2Src
        flow2Updated.destination == flow2Dst

        (flow1Path == PathHelper.convert(northbound.getFlowPath(flow1.id))) == pathNotChanged
        (flow2Path == PathHelper.convert(northbound.getFlowPath(flow2.id))) == pathNotChanged

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        endpointsPart << ["vlans"] * 4 + ["ports"] * 4 + ["switches"] * 4
        description << ["src <-> src", "dst <-> dst", "src <-> dst", "dst <-> src"] * 3
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 12
        flow2SwitchPair << [getHalfDifferentNotNeighboringSwitchPair(flow1SwitchPair, "dst", "src")] * 12
        flow1 << [getFlowHelper().randomFlow(flow1SwitchPair)] * 12
        flow2 << [getFlowHelper().randomFlow(flow2SwitchPair, false, [flow1])] * 12
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(flow1.source, "vlanId", flow2.source.vlanId), flow1.destination,
                 changePropertyValue(flow2.source, "vlanId", flow1.source.vlanId), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "vlanId", flow2.destination.vlanId),
                 flow2.source, changePropertyValue(flow2.destination, "vlanId", flow1.destination.vlanId)],
                [changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)],
                [flow1.source, changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId),
                 changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId), flow2.destination],

                [changePropertyValue(flow1.source, "portNumber", flow2.source.portNumber), flow1.destination,
                 changePropertyValue(flow2.source, "portNumber", flow1.source.portNumber), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.destination.portNumber),
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.destination.portNumber)],
                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber), flow2.destination],

                [changePropertyValue(flow1.source, "datapath", flow2.source.datapath), flow1.destination,
                 changePropertyValue(flow2.source, "datapath", flow1.source.datapath), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "datapath", flow2.destination.datapath),
                 flow2.source, changePropertyValue(flow2.destination, "datapath", flow1.destination.datapath)],
                [changePropertyValue(flow1.source, "datapath", flow2.destination.datapath), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "datapath", flow1.source.datapath)],
                [flow1.source, changePropertyValue(flow1.destination, "datapath", flow2.source.datapath),
                 changePropertyValue(flow2.source, "datapath", flow1.destination.datapath), flow2.destination],
        ]
        pathNotChanged << [true] * 8 + [false, true, false, false]
    }

    @Unroll
    def "Able to swap endpoints (#description) for two flows with different source and the same destination \
switches"() {
        given: "Two flows with different source and the same destination switches"
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

        (flow1Path == PathHelper.convert(northbound.getFlowPath(flow1.id))) == pathNotChanged
        (flow2Path == PathHelper.convert(northbound.getFlowPath(flow2.id))) == pathNotChanged

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["src <-> src", "dst <-> dst", "src <-> dst", "dst <-> src"]
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow2SwitchPair << [getHalfDifferentNotNeighboringSwitchPair(flow1SwitchPair, "dst", "src")] * 4
        flow1 << [getFlowHelper().randomFlow(flow1SwitchPair)] * 4
        flow2 << [getFlowHelper().randomFlow(flow2SwitchPair, false, [flow1])] * 4
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
        pathNotChanged << [false, true, false, false]
    }

    @Unroll
    def "Able to swap #endpointsPart (#description) for two flows with different source and destination switches"() {
        given: "Two flows with different source and destination switches"
        flowHelper.addFlow(flow1)
        flowHelper.addFlow(flow2)

        when: "Try to swap #endpointsPart for flows"
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        def response = northbound.swapFlowEndpoint(
                new SwapFlowPayload(flow1.id, flow1Src, flow1Dst),
                new SwapFlowPayload(flow2.id, flow2Src, flow2Dst))

        def flow1Updated = northbound.getFlow(flow1.id)
        def flow2Updated = northbound.getFlow(flow2.id)

        then: "#endpointsPart.capitalize() are successfully swapped"
        response.firstFlow.source == flow1Src
        response.firstFlow.destination == flow1Dst
        response.secondFlow.source == flow2Src
        response.secondFlow.destination == flow2Dst

        flow1Updated.source == flow1Src
        flow1Updated.destination == flow1Dst
        flow2Updated.source == flow2Src
        flow2Updated.destination == flow2Dst

        (flow1Path == PathHelper.convert(northbound.getFlowPath(flow1.id))) == pathNotChanged
        (flow2Path == PathHelper.convert(northbound.getFlowPath(flow2.id))) == pathNotChanged

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        endpointsPart << ["vlans"] * 4 + ["ports"] * 4 + ["switches"] * 4
        description << ["src <-> src", "dst <-> dst", "src <-> dst", "dst <-> src"] * 3
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 12
        flow2SwitchPair << [getDifferentNotNeighboringSwitchPair(flow1SwitchPair)] * 12
        flow1 << [getFlowHelper().randomFlow(flow1SwitchPair)] * 12
        flow2 << [getFlowHelper().randomFlow(flow2SwitchPair)] * 12
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [changePropertyValue(flow1.source, "vlanId", flow2.source.vlanId), flow1.destination,
                 changePropertyValue(flow2.source, "vlanId", flow1.source.vlanId), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "vlanId", flow2.destination.vlanId),
                 flow2.source, changePropertyValue(flow2.destination, "vlanId", flow1.destination.vlanId)],
                [changePropertyValue(flow1.source, "vlanId", flow2.destination.vlanId), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "vlanId", flow1.source.vlanId)],
                [flow1.source, changePropertyValue(flow1.destination, "vlanId", flow2.source.vlanId),
                 changePropertyValue(flow2.source, "vlanId", flow1.destination.vlanId), flow2.destination],

                [changePropertyValue(flow1.source, "portNumber", flow2.source.portNumber), flow1.destination,
                 changePropertyValue(flow2.source, "portNumber", flow1.source.portNumber), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.destination.portNumber),
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.destination.portNumber)],
                [changePropertyValue(flow1.source, "portNumber", flow2.destination.portNumber), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "portNumber", flow1.source.portNumber)],
                [flow1.source, changePropertyValue(flow1.destination, "portNumber", flow2.source.portNumber),
                 changePropertyValue(flow2.source, "portNumber", flow1.destination.portNumber), flow2.destination],

                [changePropertyValue(flow1.source, "datapath", flow2.source.datapath), flow1.destination,
                 changePropertyValue(flow2.source, "datapath", flow1.source.datapath), flow2.destination],
                [flow1.source, changePropertyValue(flow1.destination, "datapath", flow2.destination.datapath),
                 flow2.source, changePropertyValue(flow2.destination, "datapath", flow1.destination.datapath)],
                [changePropertyValue(flow1.source, "datapath", flow2.destination.datapath), flow1.destination,
                 flow2.source, changePropertyValue(flow2.destination, "datapath", flow1.source.datapath)],
                [flow1.source, changePropertyValue(flow1.destination, "datapath", flow2.source.datapath),
                 changePropertyValue(flow2.source, "datapath", flow1.destination.datapath), flow2.destination],
        ]
        pathNotChanged << [true] * 8 + [false, false, false, false]
    }

    @Unroll
    def "Able to swap endpoints (#description) for two flows with different source and destination switches"() {
        given: "Two flows with different source and destination switches"
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

        flow1Path != PathHelper.convert(northbound.getFlowPath(flow1.id))
        flow2Path != PathHelper.convert(northbound.getFlowPath(flow2.id))

        and: "Delete flows"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }

        where:
        description << ["src <-> src", "dst <-> dst", "src <-> dst", "dst <-> src"]
        flow1SwitchPair << [getTopologyHelper().getNotNeighboringSwitchPair()] * 4
        flow2SwitchPair << [getDifferentNotNeighboringSwitchPair(flow1SwitchPair)] * 4
        flow1 << [getFlowHelper().randomFlow(flow1SwitchPair)] * 4
        flow2 << [getFlowHelper().randomFlow(flow2SwitchPair)] * 4
        [flow1Src, flow1Dst, flow2Src, flow2Dst] << [
                [flow2.source, flow1.destination, flow1.source, flow2.destination],
                [flow1.source, flow2.destination, flow2.source, flow1.destination],
                [flow2.destination, flow1.destination, flow2.source, flow1.source],
                [flow1.source, flow2.source, flow1.destination, flow2.destination]
        ]
    }

    FlowEndpointPayload changePropertyValue(FlowEndpointPayload object, String propertyName, newValue) {
        // Deep copy of object
        def mapper = new ObjectMapper()
        return mapper.readValue(mapper.writeValueAsString(object), FlowEndpointPayload).tap {
            it."$propertyName" = newValue
        }
    }

    SwitchPair getHalfDifferentNotNeighboringSwitchPair(SwitchPair switchPairToDiffer, String equalEndpoint,
                                                        String differentEndpoint) {
        getTopologyHelper().getAllNotNeighboringSwitchPairs().find {
            it."$equalEndpoint" == switchPairToDiffer."$equalEndpoint" &&
                    it."$differentEndpoint" != switchPairToDiffer."$differentEndpoint"
        }
    }

    SwitchPair getDifferentNotNeighboringSwitchPair(SwitchPair switchPairToDiffer) {
        getTopologyHelper().getAllNotNeighboringSwitchPairs().find {
            it.src != switchPairToDiffer.src && it.dst != switchPairToDiffer.dst
        }
    }
}
