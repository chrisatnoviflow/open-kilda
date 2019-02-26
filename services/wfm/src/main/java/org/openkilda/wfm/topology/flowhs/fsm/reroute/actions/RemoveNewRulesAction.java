/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import org.openkilda.floodlight.flow.request.FlowRequest;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.AbstractFlowCommandFactory;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandFactory;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class RemoveNewRulesAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final AbstractFlowCommandFactory commandFactory;

    public RemoveNewRulesAction(PersistenceManager persistenceManager) {
        super(persistenceManager);

        this.commandFactory = new AbstractFlowCommandFactory(persistenceManager);
    }

    @Override
    protected void perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                           FlowRerouteFsm.Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine)
            throws FlowProcessingException {
        Flow currentFlow = getFlow(stateMachine.getFlowId());
        FlowPath newForward = getFlowPath(currentFlow, stateMachine.getNewForwardPath());
        FlowPath newReverse = getFlowPath(currentFlow, stateMachine.getNewReversePath());

        FlowCommandFactory flowCommandFactory = commandFactory.getFactory(currentFlow.getEncapsulationType());
        List<RemoveRule> nonIngressCommands = flowCommandFactory.createRemoveNonIngressRules(
                stateMachine.getCommandContext(), currentFlow, newForward, newReverse);
        List<RemoveRule> ingressCommands = flowCommandFactory.createRemoveIngressRules(
                stateMachine.getCommandContext(), currentFlow, newForward, newReverse);
        List<RemoveRule> commands = ListUtils.union(nonIngressCommands, ingressCommands);
        stateMachine.setRemoveCommands(commands.stream()
                .collect(Collectors.toMap(RemoveRule::getCommandId, Function.identity())));

        Set<UUID> commandIds = commands.stream()
                .peek(command -> stateMachine.getCarrier().sendSpeakerRequest(command))
                .map(FlowRequest::getCommandId)
                .collect(Collectors.toSet());
        stateMachine.setPendingCommands(commandIds);

        log.debug("Commands for removing rules have been sent for the flow {}", stateMachine.getFlowId());

        saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(),
                "Remove commands for new rules have been sent.");
    }
}
