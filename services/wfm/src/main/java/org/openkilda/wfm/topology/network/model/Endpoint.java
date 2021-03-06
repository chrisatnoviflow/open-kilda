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

package org.openkilda.wfm.topology.network.model;

import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.model.SwitchId;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.io.Serializable;

@Value
@AllArgsConstructor(staticName = "of")
@EqualsAndHashCode
public class Endpoint implements Serializable {
    private SwitchId datapath;

    private int portNumber;

    public Endpoint(NetworkEndpoint networkEndpoint) {
        this(networkEndpoint.getDatapath(), networkEndpoint.getPortNumber());
    }

    public Endpoint(PathNode pathNode) {
        this(pathNode.getSwitchId(), pathNode.getPortNo());
    }

    @Override
    public String toString() {
        return String.format("%s_%d", datapath, portNumber);
    }
}
