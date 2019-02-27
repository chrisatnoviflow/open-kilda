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

import static org.openkilda.messaging.Utils.FLOW_ID;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstallEgressRule extends InstallTransitRule {

    /**
     * Output action on the vlan tag.
     */
    @JsonProperty("output_vlan_type")
    protected OutputVlanType outputVlanType;

    /**
     * Output vlan id value.
     */
    @JsonProperty("output_vlan_id")
    protected Integer outputVlanId;

    @JsonCreator
    @Builder
    public InstallEgressRule(@JsonProperty("message_context") MessageContext messageContext,
                             @JsonProperty(FLOW_ID) final String id,
                             @JsonProperty("cookie") final Long cookie,
                             @JsonProperty("switch_id") final SwitchId switchId,
                             @JsonProperty("input_port") final Integer inputPort,
                             @JsonProperty("output_port") final Integer outputPort,
                             @JsonProperty("transit_vlan_id") final Integer transitVlanId,
                             @JsonProperty("output_vlan_type") final OutputVlanType outputVlanType,
                             @JsonProperty("output_vlan_id") final Integer outputVlanId) {
        super(messageContext, id, cookie, switchId, inputPort, outputPort, transitVlanId);
        this.outputVlanType = outputVlanType;
        this.outputVlanId = outputVlanId;
    }
}