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

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class SnowcastEpoch {

    private static final long INITIALIZATION_TIMESTAMP = System.currentTimeMillis();
    private static final long INITIALIZATION_NANOTIME = System.nanoTime();

    private final long offset;

    private SnowcastEpoch(long offset) {
        this.offset = offset;
    }

    public long getEpochTimestamp() {
        return getEpochTimestamp(getNow());
    }

    public long getEpochTimestamp(long timestamp) {
        return timestamp - offset;
    }

    public long getEpochOffset() {
        return offset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SnowcastEpoch epoch = (SnowcastEpoch) o;

        if (offset != epoch.offset) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return (int) (offset ^ (offset >>> 32));
    }

    @Override
    public String toString() {
        return "SnowcastEpoch{" + "offset=" + offset + '}';
    }

    public static SnowcastEpoch byCalendar(Calendar calendar) {
        long offset = calendar.getTimeInMillis();
        return new SnowcastEpoch(offset);
    }

    public static SnowcastEpoch byTimestamp(long timestamp) {
        return new SnowcastEpoch(timestamp);
    }

    private static long getNow() {
        long nanoTime = System.nanoTime();
        long delta = nanoTime - INITIALIZATION_NANOTIME;
        return INITIALIZATION_TIMESTAMP + TimeUnit.NANOSECONDS.toMillis(delta);
    }
}
