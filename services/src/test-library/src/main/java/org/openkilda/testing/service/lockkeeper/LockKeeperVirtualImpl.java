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
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.service.lockkeeper.model.SwitchModify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Provide functionality of {@link LockKeeperService} for virtual network.
 */
@Slf4j
@Service
@Profile("virtual")
public class LockKeeperVirtualImpl extends LockKeeperServiceImpl {

    @Value("#{'${floodlight.controller.uri}'.split(',')}")
    private List<String> controllerHost;

    @Autowired
    private TopologyDefinition topology;

    private TopologyDefinition.Switch getSwitchBySwitchId(SwitchId switchId) {
        return topology.getSwitches().stream()
                .filter(sw -> Objects.equals(switchId, sw.getDpId()))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Switch with dpid %s is not found", switchId.toString())));
    }

    @Override
    public void knockoutSwitch(SwitchId switchId) {
        String swName = getSwitchBySwitchId(switchId).getName();
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/knockoutswitch", HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(swName, null), buildJsonHeaders()), String.class);
        log.debug("Knocking out switch: {}", swName);
    }

    @Override
    public void reviveSwitch(SwitchId switchId) {
        String swName = getSwitchBySwitchId(switchId).getName();
        restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/reviveswitch", HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(swName, null),
                        buildJsonHeaders()), String.class);
        log.debug("Revive switch: {}", swName);
    }

    @Override
    public ResponseEntity<String> setManagementControllerOnSwitch(SwitchId switchId) {
        String swName = getSwitchBySwitchId(switchId).getName();
        log.debug("Trying to set management controller on the switch: {}", swName);
        return restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/set-management-controller-only",
                HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(swName, null), buildJsonHeaders()), String.class);
    }

    @Override
    public ResponseEntity<String> getControllerOnSwitch(SwitchId switchId) {
        String swName = getSwitchBySwitchId(switchId).getName();
        log.debug("Trying to set management controller on the switch: {}", swName);
        return restTemplate.exchange(labService.getLab().getLabId() + "/lock-keeper/get-controller-on-switch",
                HttpMethod.POST,
                new HttpEntity<>(new SwitchModify(swName, null), buildJsonHeaders()), String.class);
    }
}
