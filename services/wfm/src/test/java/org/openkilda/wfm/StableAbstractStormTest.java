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

package org.openkilda.wfm;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.Config;
import org.apache.storm.testing.CompleteTopologyParam;
import org.apache.storm.testing.MkClusterParam;

//todo: should be removed when all tests will be free from kafka usages
@Slf4j
public class StableAbstractStormTest extends AbstractStormTest {
    protected static MkClusterParam clusterParam;
    protected static CompleteTopologyParam completeTopologyParam;

    protected static void startCompleteTopology() throws Exception {
        log.info("Starting CompleteTopology ...");

        clusterParam = new MkClusterParam();
        clusterParam.setSupervisors(1);
        Config daemonConfig = new Config();
        daemonConfig.put(Config.STORM_LOCAL_MODE_ZMQ, false);
        clusterParam.setDaemonConf(daemonConfig);
        makeConfigFile();

        Config conf = new Config();
        conf.setNumWorkers(1);

        completeTopologyParam = new CompleteTopologyParam();
        completeTopologyParam.setStormConf(conf);
    }
}
