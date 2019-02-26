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

package org.openkilda.wfm.topology.flowhs.fsm;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.bolts.FlowHistorySupportingCarrier;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.time.Instant;

@Slf4j
public abstract class FlowProcessingAction<T extends NbTrackableStateMachine<T, S, E, C>, S, E, C>
        extends AnonymousAction<T, S, E, C> {

    protected final PersistenceManager persistenceManager;
    protected final FlowRepository flowRepository;
    protected final FlowPathRepository flowPathRepository;

    private Flow flow;

    public FlowProcessingAction(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
    }

    @Override
    public final void execute(S from, S to, E event, C context, T stateMachine) {
        try {
            perform(from, to, event, context, stateMachine);
        } catch (Exception e) {
            log.error("Flow processing failure", e);

            stateMachine.fireError();
        }
    }

    protected abstract void perform(S from, S to, E event, C context, T stateMachine)
            throws FlowProcessingException;

    protected Flow getFlow(String flowId) throws FlowProcessingException {
        if (flow == null) {
            flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                            getGenericErrorMessage(), format("Flow %s not found", flowId)));
        }

        return flow;
    }

    protected FlowPath getFlowPath(Flow flow, PathId pathId) throws FlowProcessingException {
        return flow.getPaths().stream()
                .filter(path -> path.getPathId().equals(pathId))
                .findAny()
                .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                        getGenericErrorMessage(), format("Flow path %s not found", pathId)));
    }

    protected FlowPath getFlowPath(String flowId, PathId pathId) throws FlowProcessingException {
        return getFlowPath(getFlow(flowId), pathId);
    }

    protected String getGenericErrorMessage() {
        return "Could not reroute flow";
    }

    protected void saveHistory(T stateMachine, FlowHistorySupportingCarrier carrier, String flowId, String action) {
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .time(Instant.now())
                        .flowId(flowId)
                        .build())
                .build();
        carrier.sendHistoryUpdate(historyHolder);
    }
}
