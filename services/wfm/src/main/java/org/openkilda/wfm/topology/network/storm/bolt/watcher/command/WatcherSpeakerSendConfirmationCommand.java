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

package org.openkilda.wfm.topology.network.storm.bolt.watcher.command;

import org.openkilda.messaging.info.discovery.DiscoPacketSendingConfirmation;
import org.openkilda.wfm.topology.network.model.Endpoint;
import org.openkilda.wfm.topology.network.storm.bolt.watcher.WatcherHandler;

public class WatcherSpeakerSendConfirmationCommand extends WatcherCommand {
    private final DiscoPacketSendingConfirmation confirmation;

    public WatcherSpeakerSendConfirmationCommand(DiscoPacketSendingConfirmation confirmation) {
        super(new Endpoint(confirmation.getEndpoint()));
        this.confirmation = confirmation;
    }

    @Override
    public void apply(WatcherHandler handler) {
        handler.processConfirmation(getEndpoint(), confirmation.getPacketId());
    }
}
