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

package org.openkilda.wfm.topology.flowhs.fsm.create;

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowCreateHubCarrier;
import org.openkilda.wfm.topology.flowhs.fsm.NbTrackableStateMachine;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.CompleteFlowCreateAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.DumpIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.DumpNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.FlowValidateAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.HandleNotCreatedFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.HandleNotDeletedRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.InstallNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.OnReceivedDeleteResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.ResourcesAllocateAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.ResourcesDeallocateAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.RollbackInstalledRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.ValidateIngressRuleAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.action.ValidateNonIngressRuleAction;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Slf4j
public final class FlowCreateFsm extends NbTrackableStateMachine<FlowCreateFsm, State, Event, FlowCreateContext> {

    private Flow flow;
    private FlowCreateHubCarrier carrier;
    private FlowResources flowResources;

    private Set<String> pendingCommands = new HashSet<>();

    private Map<String, InstallIngressRule> ingressCommands = new HashMap<>();
    private Map<String, InstallTransitRule> nonIngressCommands = new HashMap<>();
    private Map<String, RemoveRule> removeCommands = new HashMap<>();

    private FlowCreateFsm(CommandContext commandContext, FlowCreateHubCarrier carrier) {
        super(commandContext);
        this.carrier = carrier;
    }

    /**
     * Returns builder for flow create fsm.
     */
    private static StateMachineBuilder<FlowCreateFsm, State, Event, FlowCreateContext> builder(
            PersistenceManager persistenceManager, FlowResourcesConfig resourcesConfig, PathComputer pathComputer) {
        StateMachineBuilder<FlowCreateFsm, State, Event, FlowCreateContext> builder = StateMachineBuilderFactory.create(
                FlowCreateFsm.class, State.class, Event.class, FlowCreateContext.class,
                CommandContext.class, FlowCreateHubCarrier.class);

        // validate the flow
        builder.transition()
                .from(State.INITIALIZED)
                .to(State.FLOW_VALIDATED)
                .on(Event.NEXT)
                .perform(new FlowValidateAction(persistenceManager));

        // allocate flow resources
        builder.transition()
                .from(State.FLOW_VALIDATED)
                .to(State.ALLOCATING_RESOURCES)
                .on(Event.NEXT)
                .perform(new ResourcesAllocateAction(pathComputer, persistenceManager, resourcesConfig));

        // install and validate transit and egress rules
        builder.externalTransition()
                .from(State.ALLOCATING_RESOURCES)
                .to(State.INSTALLING_NON_INGRESS_RULES)
                .on(Event.NEXT)
                .perform(new InstallNonIngressRulesAction(persistenceManager));

        builder.internalTransition()
                .within(State.INSTALLING_NON_INGRESS_RULES)
                .on(Event.COMMAND_EXECUTED)
                .perform(new OnReceivedInstallResponseAction());

        builder.transition()
                .from(State.INSTALLING_NON_INGRESS_RULES)
                .to(State.VALIDATING_NON_INGRESS_RULES)
                .on(Event.NEXT)
                .perform(new DumpNonIngressRulesAction());
        builder.internalTransition()
                .within(State.VALIDATING_NON_INGRESS_RULES)
                .on(Event.COMMAND_EXECUTED)
                .perform(new ValidateNonIngressRuleAction());

        // verify installed transit and egress rules
        builder.transitions()
                .from(State.VALIDATING_NON_INGRESS_RULES)
                .toAmong(State.INSTALLING_INGRESS_RULES)
                .onEach(Event.NEXT)
                .perform(new InstallIngressRulesAction(persistenceManager));

        builder.internalTransition()
                .within(State.INSTALLING_INGRESS_RULES)
                .on(Event.COMMAND_EXECUTED)
                .perform(new OnReceivedInstallResponseAction());
        builder.transition()
                .from(State.INSTALLING_INGRESS_RULES)
                .to(State.VALIDATING_INGRESS_RULES)
                .on(Event.NEXT)
                .perform(new DumpIngressRulesAction());

        builder.internalTransition()
                .within(State.VALIDATING_INGRESS_RULES)
                .on(Event.COMMAND_EXECUTED)
                .perform(new ValidateIngressRuleAction());
        builder.transition()
                .from(State.VALIDATING_INGRESS_RULES)
                .to(State.FINISHED)
                .on(Event.NEXT)
                .perform(new CompleteFlowCreateAction(persistenceManager));

        // error during validation or resource allocation
        builder.transition()
                .from(State.INITIALIZED)
                .to(State.FINISHED_WITH_ERROR)
                .on(Event.ERROR);

        builder.transitions()
                .from(State.FLOW_VALIDATED)
                .toAmong(State.FINISHED_WITH_ERROR, State.FINISHED_WITH_ERROR)
                .onEach(Event.TIMEOUT, Event.ERROR);

        // rollback in case of error
        builder.transitions()
                .from(State.INSTALLING_NON_INGRESS_RULES)
                .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                .onEach(Event.TIMEOUT, Event.ERROR)
                .perform(new RollbackInstalledRulesAction(persistenceManager));

        builder.transitions()
                .from(State.VALIDATING_NON_INGRESS_RULES)
                .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                .onEach(Event.TIMEOUT, Event.ERROR)
                .perform(new RollbackInstalledRulesAction(persistenceManager));

        builder.transitions()
                .from(State.INSTALLING_INGRESS_RULES)
                .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                .onEach(Event.TIMEOUT, Event.ERROR)
                .perform(new RollbackInstalledRulesAction(persistenceManager));

        builder.transitions()
                .from(State.VALIDATING_INGRESS_RULES)
                .toAmong(State.REMOVING_RULES, State.REMOVING_RULES)
                .onEach(Event.TIMEOUT, Event.ERROR)
                .perform(new RollbackInstalledRulesAction(persistenceManager));

        // rules deletion
        builder.internalTransition()
                .within(State.REMOVING_RULES)
                .on(Event.COMMAND_EXECUTED)
                .perform(new OnReceivedDeleteResponseAction());
        builder.transition()
                .from(State.REMOVING_RULES)
                .to(State.FINISHED_WITH_ERROR)
                .on(Event.NEXT)
                .perform(new HandleNotCreatedFlowAction(persistenceManager));
        builder.transitions()
                .from(State.REMOVING_RULES)
                .toAmong(State.NON_DELETED_RULES_STORED, State.NON_DELETED_RULES_STORED)
                .onEach(Event.TIMEOUT, Event.ERROR)
                .perform(new HandleNotDeletedRulesAction());

        builder.transition()
                .from(State.NON_DELETED_RULES_STORED)
                .to(State.RESOURCES_DE_ALLOCATED)
                .on(Event.NEXT)
                .perform(new ResourcesDeallocateAction(resourcesConfig, persistenceManager));

        builder.transition()
                .from(State.RESOURCES_DE_ALLOCATED)
                .toFinal(State.FINISHED_WITH_ERROR)
                .on(Event.NEXT)
                .perform(new HandleNotCreatedFlowAction(persistenceManager));

        return builder;
    }

    @Override
    protected void afterTransitionCausedException(State fromState, State toState, Event event,
                                                  FlowCreateContext context) {
        if (fromState == State.INITIALIZED || fromState == State.FLOW_VALIDATED) {
            ErrorData error = new ErrorData(ErrorType.INTERNAL_ERROR, "Could not create flow",
                    getLastException().getMessage());
            Message message = new ErrorMessage(error, getCommandContext().getCreateTime(),
                    getCommandContext().getCorrelationId());
            carrier.sendNorthboundResponse(message);
        }
        super.afterTransitionCausedException(fromState, toState, event, context);
    }

    @Override
    public void fireNext(FlowCreateContext context) {
        fire(Event.NEXT, context);
    }

    @Override
    public void fireError() {
        fire(Event.ERROR);
    }

    @Override
    public void sendResponse(Message message) {
        carrier.sendNorthboundResponse(message);
    }

    public static FlowCreateFsm newInstance(CommandContext commandContext, FlowCreateHubCarrier carrier,
                                            PersistenceManager persistenceManager,
                                            FlowResourcesConfig resourcesConfig, PathComputer pathComputer) {
        return builder(persistenceManager, resourcesConfig, pathComputer)
                .newStateMachine(State.INITIALIZED, commandContext, carrier);
    }

    public enum State {
        INITIALIZED,
        FLOW_VALIDATED,
        ALLOCATING_RESOURCES,
        INSTALLING_NON_INGRESS_RULES,
        VALIDATING_NON_INGRESS_RULES,
        INSTALLING_INGRESS_RULES,
        VALIDATING_INGRESS_RULES,
        FINISHED,

        REMOVING_RULES,
        VALIDATING_REMOVED_RULES,
        RESOURCES_DE_ALLOCATED,
        NON_DELETED_RULES_STORED,
        FINISHED_WITH_ERROR,
    }

    public enum Event {
        NEXT,
        COMMAND_EXECUTED,
        TIMEOUT,
        ERROR
    }
}
