/* Copyright 2018 Telstra Open Source
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

package org.openkilda.testing.service.lockkeeper;

import org.openkilda.model.SwitchId;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;

import java.util.List;

/**
 * This service is meant to give control over some software or hardware parts of the system that are out of Kilda's
 * direct control. E.g. switches that are not connected to controller or lifecycle of system components.
 */
public interface LockKeeperService {
    void addFlows(List<ASwitchFlow> flows);

    void removeFlows(List<ASwitchFlow> flows);

    List<ASwitchFlow> getAllFlows();

    void portsUp(List<Integer> ports);

    void portsDown(List<Integer> ports);

    /**
     * Please use the setController(SwitchId switchId) method instead.
     */
    @Deprecated
    void knockoutSwitch(SwitchId switchId);

    /**
     * Please use the setController(SwitchId switchId, controllers) method instead,
     * where controllers = managementController + " " + statController .
     */
    @Deprecated
    void reviveSwitch(SwitchId switchId);

    void stopFloodlight();

    void startFloodlight();

    void restartFloodlight();

    void setController(SwitchId switchId);

    void setController(SwitchId switchId, String controller);
}
