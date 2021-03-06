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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.HazelcastInstanceProxy;
import com.hazelcast.nio.ClassLoaderUtil;
import com.noctarius.snowcast.impl.NodeSnowcastFactory;
import com.noctarius.snowcast.impl.SequencerService;

import java.lang.reflect.Method;
import java.util.Map;

import static com.noctarius.snowcast.impl.SnowcastConstants.USER_CONTEXT_LOOKUP_NAME;

public final class SnowcastSystem {

    private SnowcastSystem() {
    }

    public static Snowcast snowcast(HazelcastInstance hazelcastInstance) {
        // Test for an already created instance first
        Map<String, Object> userContext = hazelcastInstance.getUserContext();
        Snowcast snowcast = (Snowcast) userContext.get(USER_CONTEXT_LOOKUP_NAME);
        if (snowcast != null) {
            return snowcast;
        }

        // Node setup
        if (hazelcastInstance instanceof HazelcastInstanceProxy) {
            snowcast = NodeSnowcastFactory.snowcast(hazelcastInstance);
        }

        if (snowcast == null) {
            try {
                String className = SequencerService.class.getPackage().getName() + ".ClientSnowcastFactory";
                Class<?> clazz = ClassLoaderUtil.loadClass(null, className);
                Method snowcastMethod = clazz.getMethod("snowcast", HazelcastInstance.class);
                snowcast = (Snowcast) snowcastMethod.invoke(clazz, hazelcastInstance);

            } catch (Exception e) {
                if (e instanceof SnowcastException) {
                    throw (SnowcastException) e;
                }
                throw new SnowcastException(e);
            }
        }

        userContext.put(USER_CONTEXT_LOOKUP_NAME, snowcast);
        return snowcast;
    }
}
