/*
 * Copyright (c) 2014, Christoph Engelbert (aka noctarius) and
 * contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.noctarius.snowcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.ServiceConfig;
import com.hazelcast.config.ServicesConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.noctarius.snowcast.impl.NodeSequencerService;
import com.noctarius.snowcast.impl.SnowcastConstants;

public final class SnowcastNodeConfigurator {

    private SnowcastNodeConfigurator() {
    }

    public static Config buildSnowcastAwareConfig() {
        return buildSnowcastAwareConfig(new XmlConfigBuilder().build());
    }

    public static Config buildSnowcastAwareConfig(Config config) {
        ServicesConfig servicesConfig = config.getServicesConfig();
        servicesConfig.addServiceConfig(new ServiceConfig().setEnabled(true).setName(SnowcastConstants.SERVICE_NAME)
                                                           .setServiceImpl(new NodeSequencerService()));
        return config;
    }
}
