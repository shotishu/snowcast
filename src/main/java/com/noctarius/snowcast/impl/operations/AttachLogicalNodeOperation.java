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
package com.noctarius.snowcast.impl.operations;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.spi.PartitionAwareOperation;
import com.noctarius.snowcast.SnowcastEpoch;
import com.noctarius.snowcast.impl.NodeSequencerService;
import com.noctarius.snowcast.impl.SequencerDataSerializerHook;
import com.noctarius.snowcast.impl.SequencerDefinition;
import com.noctarius.snowcast.impl.SequencerPartition;

import java.io.IOException;

public class AttachLogicalNodeOperation
        extends AbstractSequencerOperation
        implements PartitionAwareOperation {

    private transient Integer logicalNodeId;

    private SequencerDefinition definition;

    public AttachLogicalNodeOperation() {
    }

    public AttachLogicalNodeOperation(SequencerDefinition definition) {
        super(definition.getSequencerName());
        this.definition = definition;
    }

    @Override
    public int getId() {
        return SequencerDataSerializerHook.TYPE_ATTACH_LOGICAL_NODE;
    }

    @Override
    public void run()
            throws Exception {

        NodeSequencerService sequencerService = getService();
        SequencerPartition partition = sequencerService.getSequencerPartition(getPartitionId());
        logicalNodeId = partition.attachLogicalNode(definition, getCallerAddress());
    }

    @Override
    public boolean returnsResponse() {
        return true;
    }

    @Override
    public Object getResponse() {
        return logicalNodeId;
    }

    @Override
    protected void writeInternal(ObjectDataOutput out)
            throws IOException {

        super.writeInternal(out);
        out.writeLong(definition.getEpoch().getEpochOffset());
        out.writeInt(definition.getMaxLogicalNodeCount());
    }

    @Override
    protected void readInternal(ObjectDataInput in)
            throws IOException {

        super.readInternal(in);

        long epochOffset = in.readLong();
        int maxLogicalNodeCount = in.readInt();

        SnowcastEpoch epoch = SnowcastEpoch.byTimestamp(epochOffset);
        definition = new SequencerDefinition(getSequencerName(), epoch, maxLogicalNodeCount);
    }
}
