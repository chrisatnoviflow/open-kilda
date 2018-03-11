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

package org.openkilda.messaging.info.flow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowCacheSyncResults;

import java.util.List;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

/**
 * Captures the results of a FlowCacheSyncRequest.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowCacheSyncResponse extends InfoData {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The response payload.
     */
    @JsonProperty(Utils.PAYLOAD)
    protected FlowCacheSyncResults payload;

    /**
     * Instance constructor.
     *
     * @param payload response payload
     * @throws IllegalArgumentException if payload is null
     */
    @JsonCreator
    public FlowCacheSyncResponse(@JsonProperty(Utils.PAYLOAD) FlowCacheSyncResults payload) {
        setPayload(payload);
    }

    /**
     * Returns response payload.
     *
     * @return response payload
     */
    public FlowCacheSyncResults getPayload() {
        return payload;
    }

    /**
     * Sets response payload.
     *
     * @param payload response payload
     */
    public void setPayload(FlowCacheSyncResults payload) {
        if (payload == null) {
            throw new IllegalArgumentException("need to set payload");
        }
        this.payload = payload;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(Utils.PAYLOAD, payload)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(payload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        FlowCacheSyncResponse that = (FlowCacheSyncResponse) object;
        return Objects.equals(getPayload(), that.getPayload());
    }
}
