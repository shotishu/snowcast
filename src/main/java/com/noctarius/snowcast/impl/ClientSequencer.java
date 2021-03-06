package com.noctarius.snowcast.impl;

import com.hazelcast.client.impl.HazelcastClientInstanceImpl;
import com.hazelcast.client.impl.client.PartitionClientRequest;
import com.hazelcast.client.spi.ClientContext;
import com.hazelcast.client.spi.ClientInvocationService;
import com.hazelcast.client.spi.ClientProxy;
import com.hazelcast.client.spi.EventHandler;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.Partition;
import com.hazelcast.core.PartitionService;
import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.SerializationService;
import com.noctarius.snowcast.SnowcastException;
import com.noctarius.snowcast.SnowcastSequenceState;
import com.noctarius.snowcast.SnowcastSequencer;
import com.noctarius.snowcast.impl.notification.ClientDestroySequencerNotification;
import com.noctarius.snowcast.impl.operations.client.ClientAttachLogicalNodeRequest;
import com.noctarius.snowcast.impl.operations.client.ClientDetachLogicalNodeRequest;
import com.noctarius.snowcast.impl.operations.client.ClientRegisterChannelOperation;
import com.noctarius.snowcast.impl.operations.client.ClientRemoveChannelOperation;

public class ClientSequencer
        extends ClientProxy
        implements InternalSequencer {

    private final ClientSequencerContext sequencerContext;

    ClientSequencer(HazelcastClientInstanceImpl client, SequencerDefinition definition) {
        super(SnowcastConstants.SERVICE_NAME, definition.getSequencerName());
        this.sequencerContext = new ClientSequencerContext(client, definition);
    }

    @Override
    public String getSequencerName() {
        return sequencerContext.getSequencerName();
    }

    @Override
    public long next()
            throws InterruptedException {

        return sequencerContext.next();
    }

    @Override
    public SnowcastSequenceState getSequencerState() {
        return sequencerContext.getSequencerState();
    }

    @Override
    public SnowcastSequencer attachLogicalNode() {
        sequencerContext.attachLogicalNode();
        return this;
    }

    @Override
    public SnowcastSequencer detachLogicalNode() {
        sequencerContext.detachLogicalNode();
        return this;
    }

    @Override
    public long timestampValue(long sequenceId) {
        return sequencerContext.timestampValue(sequenceId);
    }

    @Override
    public int logicalNodeId(long sequenceId) {
        return sequencerContext.logicalNodeId(sequenceId);
    }

    @Override
    public int counterValue(long sequenceId) {
        return sequencerContext.counterValue(sequenceId);
    }

    @Override
    protected void onInitialize() {
        sequencerContext.initialize(this);
    }

    @Override
    public void stateTransition(SnowcastSequenceState newState) {
        sequencerContext.stateTransition(newState);
    }

    private static class ClientSequencerContext
            extends AbstractSequencerContext {

        private final HazelcastClientInstanceImpl client;

        private volatile String channelRegistration;

        private ClientSequencerContext(HazelcastClientInstanceImpl client, SequencerDefinition definition) {
            super(definition);
            this.client = client;
        }

        @Override
        protected int doAttachLogicalNode(SequencerDefinition definition) {
            PartitionService partitionService = client.getPartitionService();
            ClientInvocationService invocationService = client.getInvocationService();

            Partition partition = partitionService.getPartition(getSequencerName());
            int partitionId = partition.getPartitionId();

            try {
                PartitionClientRequest request = new ClientAttachLogicalNodeRequest(getSequencerName(), partitionId, definition);
                ICompletableFuture<Object> future = invocationService.invokeOnPartitionOwner(request, partitionId);
                Object response = future.get();
                return client.getSerializationService().toObject(response);
            } catch (Exception e) {
                throw new SnowcastException(e);
            }
        }

        @Override
        protected void doDetachLogicalNode(String sequencerName, int logicalNodeId) {
            PartitionService partitionService = client.getPartitionService();
            ClientInvocationService invocationService = client.getInvocationService();

            Partition partition = partitionService.getPartition(sequencerName);
            int partitionId = partition.getPartitionId();

            try {
                PartitionClientRequest request = new ClientDetachLogicalNodeRequest(sequencerName, partitionId, logicalNodeId);
                ICompletableFuture<Object> future = invocationService.invokeOnPartitionOwner(request, partitionId);
                future.get();
            } catch (Exception e) {
                throw new SnowcastException(e);
            }
        }

        private void unregisterClientChannel(ClientSequencer clientSequencer) {
            ClientRemoveChannelOperation operation = new ClientRemoveChannelOperation(getSequencerName(), channelRegistration);
            clientSequencer.stopListening(operation, channelRegistration);
        }

        public void initialize(ClientSequencer clientSequencer) {
            ClientRegisterChannelOperation operation = new ClientRegisterChannelOperation(getSequencerName());
            ClientChannelHandler handler = new ClientChannelHandler(this, clientSequencer);
            channelRegistration = clientSequencer.listen(operation, getSequencerName(), handler);
        }
    }

    private static class ClientChannelHandler
            implements EventHandler<Portable> {

        private final ClientSequencerContext sequencerContext;
        private final ClientSequencer clientSequencer;

        private ClientChannelHandler(ClientSequencerContext sequencerContext, ClientSequencer clientSequencer) {
            this.sequencerContext = sequencerContext;
            this.clientSequencer = clientSequencer;
        }

        @Override
        public void handle(Portable event) {
            ClientContext context = clientSequencer.getContext();
            SerializationService serializationService = context.getSerializationService();

            Object message = serializationService.toObject(event);
            if (message instanceof ClientDestroySequencerNotification) {
                clientSequencer.stateTransition(SnowcastSequenceState.Destroyed);
                sequencerContext.unregisterClientChannel(clientSequencer);
            }
        }

        @Override
        public void beforeListenerRegister() {
        }

        @Override
        public void onListenerRegister() {
        }
    }
}
