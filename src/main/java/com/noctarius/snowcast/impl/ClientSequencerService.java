package com.noctarius.snowcast.impl;

import com.hazelcast.client.impl.HazelcastClientInstanceImpl;
import com.hazelcast.client.impl.client.PartitionClientRequest;
import com.hazelcast.client.spi.ClientInvocationService;
import com.hazelcast.client.spi.ProxyManager;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.Partition;
import com.hazelcast.core.PartitionService;
import com.noctarius.snowcast.SnowcastEpoch;
import com.noctarius.snowcast.SnowcastException;
import com.noctarius.snowcast.SnowcastSequenceState;
import com.noctarius.snowcast.SnowcastSequencer;
import com.noctarius.snowcast.impl.operations.client.ClientCreateSequencerDefinitionRequest;
import com.noctarius.snowcast.impl.operations.client.ClientDestroySequencerDefinitionRequest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.noctarius.snowcast.impl.InternalSequencerUtils.calculateBoundedMaxLogicalNodeCount;

class ClientSequencerService
        implements SequencerService {

    private final ClientSequencerConstructorFunction sequencerConstructor;

    private final HazelcastClientInstanceImpl client;

    private final ConcurrentMap<String, SequencerProvision> provisions;

    ClientSequencerService(HazelcastClientInstanceImpl client, ProxyManager proxyManager) {
        this.client = client;
        this.sequencerConstructor = new ClientSequencerConstructorFunction(client, proxyManager);
        this.provisions = new ConcurrentHashMap<String, SequencerProvision>();
    }

    @Override
    public SnowcastSequencer createSequencer(String sequencerName, SnowcastEpoch epoch, int maxLogicalNodeCount) {
        int boundedMaxLogicalNodeCount = calculateBoundedMaxLogicalNodeCount(maxLogicalNodeCount);
        SequencerDefinition definition = new SequencerDefinition(sequencerName, epoch, boundedMaxLogicalNodeCount);

        PartitionService partitionService = client.getPartitionService();
        ClientInvocationService invocationService = client.getInvocationService();

        Partition partition = partitionService.getPartition(sequencerName);
        int partitionId = partition.getPartitionId();

        try {
            PartitionClientRequest request = new ClientCreateSequencerDefinitionRequest(sequencerName, partitionId, definition);
            ICompletableFuture<Object> future = invocationService.invokeOnPartitionOwner(request, partitionId);

            Object response = future.get();
            SequencerDefinition realDefinition = client.getSerializationService().toObject(response);
            return getOrCreateSequencerProvision(realDefinition).getSequencer();
        } catch (Exception e) {
            throw new SnowcastException(e);
        }
    }

    @Override
    public void destroySequencer(SnowcastSequencer sequencer) {
        if (!(sequencer instanceof ClientSequencer)) {
            String message = ExceptionMessages.ILLEGAL_SEQUENCER_TYPE.buildMessage();
            throw new SnowcastException(message);
        }

        ((InternalSequencer) sequencer).stateTransition(SnowcastSequenceState.Destroyed);

        PartitionService partitionService = client.getPartitionService();
        ClientInvocationService invocationService = client.getInvocationService();

        String sequencerName = sequencer.getSequencerName();

        Partition partition = partitionService.getPartition(sequencerName);
        int partitionId = partition.getPartitionId();

        try {
            PartitionClientRequest request = new ClientDestroySequencerDefinitionRequest(sequencerName, partitionId);
            ICompletableFuture<?> future = invocationService.invokeOnPartitionOwner(request, partitionId);
            future.get();
        } catch (Exception e) {
            throw new SnowcastException(e);
        }
    }

    private SequencerProvision getOrCreateSequencerProvision(SequencerDefinition definition) {
        String sequencerName = definition.getSequencerName();

        SequencerProvision provision = provisions.get(sequencerName);
        if (provision != null) {
            return provision;
        }

        synchronized (provisions) {
            provision = provisions.get(sequencerName);
            if (provision != null) {
                return provision;
            }

            provision = sequencerConstructor.createNew(definition);
            provisions.put(sequencerName, provision);
            return provision;
        }
    }
}
