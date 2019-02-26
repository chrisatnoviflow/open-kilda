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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowPathBuilder;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
public class AllocateResourcesAction extends
        NbTrackableAction<FlowRerouteFsm, FlowRerouteFsm.State, FlowRerouteFsm.Event, FlowRerouteContext> {

    private final TransactionManager transactionManager;
    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final SwitchRepository switchRepository;
    private final IslRepository islRepository;

    private final PathComputer pathComputer;
    private final FlowResourcesManager resourcesManager;
    private final FlowPathBuilder flowPathBuilder;

    public AllocateResourcesAction(PersistenceManager persistenceManager, PathComputer pathComputer,
                                   FlowResourcesManager resourcesManager) {
        this.transactionManager = persistenceManager.getTransactionManager();
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();

        this.pathComputer = pathComputer;
        this.resourcesManager = resourcesManager;
        this.flowPathBuilder = new FlowPathBuilder(switchRepository);
    }

    @Override
    protected Optional<Message> perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                                        FlowRerouteFsm.Event event, FlowRerouteContext context,
                                        FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();

        final String errorMessage = "Could not reroute flow";

        Optional<Flow> foundFlow = flowRepository.findById(flowId);
        if (!foundFlow.isPresent()) {
            log.error(errorMessage + ": Flow {} not found", flowId);

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.NOT_FOUND, errorMessage,
                    format("Flow %s not found", flowId)));
        }

        Flow currentFlow = foundFlow.get();
        stateMachine.setOldStatus(currentFlow.getStatus());

        log.debug("Finding a new path for flow {}", flowId);

        PathPair pathPair;
        try {
            pathPair = pathComputer.getPath(currentFlow);
        } catch (UnroutableFlowException | RecoverableException e) {
            log.debug(errorMessage + ": Can't find a path for flow {}", flowId);

            stateMachine.fire(Event.NO_PATH_FOUND);

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.NOT_FOUND, errorMessage,
                    "Not enough bandwidth found or path not found : " + e.getMessage()));
        }

        boolean isFoundNewPath = !flowPathBuilder.isSamePath(pathPair.getForward(), currentFlow.getForwardPath())
                || !flowPathBuilder.isSamePath(pathPair.getReverse(), currentFlow.getReversePath());
        //no need to emit changes if path wasn't changed and flow is active.
        //force means to update flow even if path is not changed.
        if (!isFoundNewPath && currentFlow.isActive() && !stateMachine.isForceReroute()) {
            log.debug("Reroute {} is unsuccessful: can't find new path.", flowId);

            stateMachine.fire(Event.REROUTE_IS_SKIPPED);

            return Optional.of(buildRerouteResponseMessage(currentFlow, null,
                    stateMachine.getCommandContext()));
        }

        log.debug("Allocating resources for flow {}", flowId);

        FlowResources flowResources;
        try {
            flowResources = resourcesManager.allocateFlowResources(currentFlow);
        } catch (ResourceAllocationException e) {
            log.debug(errorMessage + ": Failed to allocate resources", e);

            stateMachine.fire(Event.NO_RESOURCES_AVAILABLE);

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.INTERNAL_ERROR, errorMessage,
                    "Unable to allocate flow resources : " + e.getMessage()));
        }

        log.debug("Resources has been allocated: {}", flowResources);

        stateMachine.setNewResources(flowResources);

        FlowPathPair newFlowPaths;
        try {
            newFlowPaths = createFlowPaths(currentFlow, pathPair, flowResources);
        } catch (Exception e) {
            log.error(errorMessage + ": Failed to persist the flow paths", e);

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.INTERNAL_ERROR, errorMessage,
                    "Unable to persist the flow paths : " + e.getMessage()));
        }

        saveHistory(currentFlow, newFlowPaths, stateMachine);

        log.debug("New paths have been created: {}", newFlowPaths);

        stateMachine.setNewForwardPath(newFlowPaths.getForward().getPathId());
        stateMachine.setNewReversePath(newFlowPaths.getReverse().getPathId());

        return Optional.of(buildRerouteResponseMessage(currentFlow, newFlowPaths, stateMachine.getCommandContext()));
    }

    private FlowPathPair createFlowPaths(Flow currentFlow, PathPair pathPair, FlowResources flowResources) {
        return transactionManager.doInTransaction(() -> {
            long cookie = flowResources.getUnmaskedCookie();
            FlowPath newForwardPath = flowPathBuilder.buildFlowPath(currentFlow, flowResources.getForward(),
                    pathPair.getForward(), Cookie.buildForwardCookie(cookie));
            FlowPath newReversePath = flowPathBuilder.buildFlowPath(currentFlow, flowResources.getReverse(),
                    pathPair.getReverse(), Cookie.buildReverseCookie(cookie));
            FlowPathPair newFlowPaths = FlowPathPair.builder().forward(newForwardPath).reverse(newReversePath).build();

            log.debug("Persisting the paths {}", newFlowPaths);

            flowPathRepository.lockInvolvedSwitches(newForwardPath, newReversePath);

            newForwardPath.setStatus(FlowPathStatus.IN_PROGRESS);
            flowPathRepository.createOrUpdate(newForwardPath);
            newReversePath.setStatus(FlowPathStatus.IN_PROGRESS);
            flowPathRepository.createOrUpdate(newReversePath);

            updateIslsForFlowPath(newForwardPath);
            updateIslsForFlowPath(newReversePath);

            currentFlow.setStatus(FlowStatus.IN_PROGRESS);
            flowRepository.createOrUpdate(currentFlow);

            return newFlowPaths;
        });
    }

    private void updateIslsForFlowPath(FlowPath path) {
        path.getSegments().forEach(pathSegment -> {
            log.debug("Updating ISL for the path segment: {}", pathSegment);

            updateAvailableBandwidth(pathSegment.getSrcSwitch().getSwitchId(), pathSegment.getSrcPort(),
                    pathSegment.getDestSwitch().getSwitchId(), pathSegment.getDestPort());
        });
    }

    private void updateAvailableBandwidth(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort) {
        long usedBandwidth = flowPathRepository.getUsedBandwidthBetweenEndpoints(srcSwitch, srcPort,
                dstSwitch, dstPort);

        Optional<Isl> matchedIsl = islRepository.findByEndpoints(srcSwitch, srcPort, dstSwitch, dstPort);
        matchedIsl.ifPresent(isl -> {
            isl.setAvailableBandwidth(isl.getMaxBandwidth() - usedBandwidth);
            islRepository.createOrUpdate(isl);
        });
    }

    private Message buildRerouteResponseMessage(Flow currentFlow, FlowPathPair newFlowPaths,
                                                CommandContext commandContext) {
        PathInfoData currentPath = FlowPathMapper.INSTANCE.map(currentFlow.getForwardPath());
        PathInfoData resultPath = Optional.ofNullable(newFlowPaths)
                .map(flow -> FlowPathMapper.INSTANCE.map(flow.getForward()))
                .orElse(currentPath);

        FlowRerouteResponse response = new FlowRerouteResponse(resultPath, !resultPath.equals(currentPath));
        return new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
    }

    private void saveHistory(Flow flow, FlowPathPair newFlowPaths, FlowRerouteFsm stateMachine) {
        Instant timestamp = Instant.now();
        FlowDumpData oldDumpData = HistoryMapper.INSTANCE.map(flow, flow.getForwardPath(), flow.getReversePath());
        oldDumpData.setDumpType(DumpType.STATE_BEFORE);

        FlowDumpData newDumpData = HistoryMapper.INSTANCE.map(flow,
                newFlowPaths.getForward(), newFlowPaths.getReverse());
        newDumpData.setDumpType(DumpType.STATE_AFTER);

        Stream.of(oldDumpData, newDumpData).forEach(dumpData -> {
            FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                    .taskId(stateMachine.getCommandContext().getCorrelationId())
                    .flowDumpData(dumpData)
                    .flowHistoryData(FlowHistoryData.builder()
                            .action("Resources were allocated")
                            .time(timestamp)
                            .flowId(flow.getFlowId())
                            .build())
                    .flowEventData(FlowEventData.builder()
                            .flowId(flow.getFlowId())
                            .event(FlowEventData.Event.REROUTE)
                            .time(timestamp)
                            .build())
                    .build();
            stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
        });
    }
}
