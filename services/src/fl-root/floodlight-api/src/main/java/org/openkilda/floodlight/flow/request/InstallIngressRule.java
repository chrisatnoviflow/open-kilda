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

package org.openkilda.floodlight.flow.request;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstallIngressRule extends InstallMeteredRule {

    /**
     * Input vlan id value.
     */
    @JsonProperty("input_vlan_id")
    private final Integer inputVlanId;

    /**
     * Output action on the vlan tag.
     */
    @JsonProperty("output_vlan_type")
    private final OutputVlanType outputVlanType;

    public InstallIngressRule(MessageContext messageContext, String commandId, String flowId, Long cookie,
                              SwitchId switchId, Integer inputPort, Integer outputPort, Long meterId, Long bandwidth,
                              OutputVlanType outputVlanType, Integer inputVlanId) {
        super(messageContext, commandId, flowId, cookie, switchId, inputPort, outputPort, meterId, bandwidth);

        this.inputVlanId = inputVlanId;
        this.outputVlanType = outputVlanType;
    }
}
