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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowhs.bolts.FlowRerouteHubCarrier;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineLogger;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FlowRerouteService {

    @VisibleForTesting
    final Map<String, FlowRerouteFsm> fsms = new HashMap<>();
    private final FsmExecutor<FlowRerouteFsm, State, Event, FlowRerouteContext> controllerExecutor
            = new FsmExecutor<>(FlowRerouteFsm.Event.NEXT);

    private final PersistenceManager persistenceManager;
    private final PathComputer pathComputer;
    private final FlowResourcesManager flowResourcesManager;

    public FlowRerouteService(PersistenceManager persistenceManager, PathComputer pathComputer,
                              FlowResourcesManager flowResourcesManager) {
        this.persistenceManager = persistenceManager;
        this.pathComputer = pathComputer;
        this.flowResourcesManager = flowResourcesManager;
    }

    /**
     * Handles request for flow reroute.
     *
     * @param key    command identifier.
     * @param flowId request data.
     */
    public void handleRequest(String key, CommandContext commandContext, String flowId, FlowRerouteHubCarrier carrier) {
        log.debug("Handling flow reroute request with key {}", key);
        FlowRerouteFsm fsm = FlowRerouteFsm.newInstance(commandContext, carrier, persistenceManager,
                pathComputer, flowResourcesManager);
        fsms.put(key, fsm);

        StateMachineLogger fsmLogger = new StateMachineLogger(fsm);
        fsmLogger.startLogging();

        controllerExecutor.fire(fsm, FlowRerouteFsm.Event.NEXT, FlowRerouteContext.builder()
                .flowId(flowId)
                .build());

        removeIfFinished(fsm, key);
    }

    /**
     * Handles async response from worker.
     *
     * @param key command identifier.
     */
    public void handleAsyncResponse(String key, FlowResponse flowResponse) {
        log.debug("Received command completion message {}", flowResponse);
        FlowRerouteFsm fsm = fsms.get(key);
        if (fsm == null) {
            log.info("Failed to find fsm: received response with key {} for non pending fsm", key);
            return;
        }

        controllerExecutor.fire(fsm, FlowRerouteFsm.Event.COMMAND_EXECUTED, FlowRerouteContext.builder()
                .flowResponse(flowResponse)
                .build());

        removeIfFinished(fsm, key);
    }

    /**
     * Handles timeout case.
     *
     * @param key command identifier.
     */
    public void handleTimeout(String key) {
        FlowRerouteFsm fsm = fsms.get(key);

        controllerExecutor.fire(fsm, FlowRerouteFsm.Event.TIMEOUT, null);

        removeIfFinished(fsm, key);
    }

    private void removeIfFinished(FlowRerouteFsm fsm, String key) {
        if (fsm.getCurrentState() == FlowRerouteFsm.State.FINISHED
                || fsm.getCurrentState() == FlowRerouteFsm.State.FINISHED_WITH_ERROR) {
            fsms.remove(key);
        }
    }
}
