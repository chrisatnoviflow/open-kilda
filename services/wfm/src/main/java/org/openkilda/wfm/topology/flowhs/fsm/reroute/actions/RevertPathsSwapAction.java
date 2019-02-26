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

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public class RevertPathsSwapAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final TransactionManager transactionManager;

    public RevertPathsSwapAction(PersistenceManager persistenceManager) {
        super(persistenceManager);

        this.transactionManager = persistenceManager.getTransactionManager();
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        transactionManager.doInTransaction(() -> {
            Flow currentFlow = getFlow(stateMachine.getFlowId());

            final FlowPathPair newPaths = FlowPathPair.builder()
                    .forward(currentFlow.getForwardPath())
                    .reverse(currentFlow.getReversePath())
                    .build();

            FlowPath oldForward = getFlowPath(currentFlow, stateMachine.getOldForwardPath());
            if (oldForward.getStatus() != FlowPathStatus.ACTIVE) {
                oldForward.setStatus(stateMachine.getOldForwardPathStatus());
            }
            flowPathRepository.createOrUpdate(oldForward);

            FlowPath oldReverse = getFlowPath(currentFlow, stateMachine.getOldReversePath());
            if (oldReverse.getStatus() != FlowPathStatus.ACTIVE) {
                oldReverse.setStatus(stateMachine.getOldReversePathStatus());
            }
            flowPathRepository.createOrUpdate(oldReverse);

            currentFlow.setForwardPath(oldForward);
            currentFlow.setReversePath(oldReverse);

            log.debug("Swapping back the flow paths {} with {}", newPaths,
                    FlowPathPair.builder().forward(oldForward).reverse(oldReverse).build());

            flowRepository.createOrUpdate(currentFlow);

            saveHistory(currentFlow, oldForward, oldReverse, stateMachine);
        });
    }

    private void saveHistory(Flow flow, FlowPath forwardPath, FlowPath reversePath, FlowRerouteFsm stateMachine) {
        FlowDumpData flowDumpData = HistoryMapper.INSTANCE.map(flow, forwardPath, reversePath);
        flowDumpData.setDumpType(DumpType.STATE_AFTER);
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowDumpData(flowDumpData)
                .flowHistoryData(FlowHistoryData.builder()
                        .action("Flow was updated with new paths")
                        .time(Instant.now())
                        .description(format("Flow %s was updated with paths %s/%s", flow.getFlowId(),
                                forwardPath.getPathId(), reversePath.getPathId()))
                        .flowId(flow.getFlowId())
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}
