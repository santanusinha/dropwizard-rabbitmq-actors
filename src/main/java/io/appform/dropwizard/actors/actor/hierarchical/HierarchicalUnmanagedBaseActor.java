package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.actor.hierarchical.tree.HierarchicalDataStoreSupplierTree;
import io.appform.dropwizard.actors.actor.hierarchical.tree.HierarchicalTreeConfig;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.HierarchicalRoutingKey;
import io.appform.dropwizard.actors.common.ErrorCode;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;


/**
 * <p>This actor maintain all actors hierarchy as per provided hierarchical actor config.
 * This is not Managed and will not be automatically started by dropwizard.</p>
 */
@Data
@EqualsAndHashCode
@ToString
@Slf4j
public class HierarchicalUnmanagedBaseActor<MessageType extends Enum<MessageType>, Message> {

    private final HierarchicalTreeConfig<HierarchicalActorConfig, String, HierarchicalOperationWorkerConfig> hierarchicalTreeConfig;
    private final ConnectionRegistry connectionRegistry;
    private final ObjectMapper mapper;
    private final RetryStrategyFactory retryStrategyFactory;
    private final ExceptionHandlingFactory exceptionHandlingFactory;
    private final Class<? extends Message> clazz;
    private final Set<Class<?>> droppedExceptionTypes;
    private final MessageType messageType;
    private final MessageHandlingFunction<Message, Boolean> handlerFunction;
    private final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction;

    private HierarchicalDataStoreSupplierTree<
                        HierarchicalOperationWorkerConfig,
                        HierarchicalActorConfig,
                        MessageType,
                        HierarchicalOperationWorker<MessageType, ? extends Message>> worker;


    public HierarchicalUnmanagedBaseActor(MessageType messageType,
                                          HierarchicalActorConfig hierarchicalActorConfig,
                                          ConnectionRegistry connectionRegistry,
                                          ObjectMapper mapper,
                                          RetryStrategyFactory retryStrategyFactory,
                                          ExceptionHandlingFactory exceptionHandlingFactory,
                                          Class<? extends Message> clazz,
                                          Set<Class<?>> droppedExceptionTypes,
                                          MessageHandlingFunction<Message, Boolean> handlerFunction,
                                          MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction) {
        this.messageType = messageType;
        this.hierarchicalTreeConfig = new HierarchicalTreeConfig<>(hierarchicalActorConfig, hierarchicalActorConfig.getChildren());
        this.connectionRegistry = connectionRegistry;
        this.mapper = mapper;
        this.retryStrategyFactory = retryStrategyFactory;
        this.exceptionHandlingFactory = exceptionHandlingFactory;
        this.clazz = clazz;
        this.droppedExceptionTypes = droppedExceptionTypes;
        this.handlerFunction = handlerFunction;
        this.expiredMessageHandlingFunction = expiredMessageHandlingFunction;
    }

    public void start() throws Exception {
        log.info("Initializing Router");
        this.initializeRouter();
        log.info("Staring all workers");
        worker.traverse(hierarchicalOperationWorker -> {
            try {
                log.info("Starting worker: {} {}", hierarchicalOperationWorker.getType(), hierarchicalOperationWorker.getRoutingKey().getRoutingKey());
                hierarchicalOperationWorker.start();
            } catch (Exception e) {
                log.error("Unable to start worker: {}", hierarchicalOperationWorker);
                val errorMessage = "Unable to start worker: " + hierarchicalOperationWorker.getType();
                throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR, errorMessage, e);
            }
        });
    }

    public void stop() throws Exception {
        log.info("Stopping all workers");
        worker.traverse(hierarchicalOperationWorker -> {
            try {
                log.info("Stopping worker: {} {}", hierarchicalOperationWorker.getType(), hierarchicalOperationWorker.getRoutingKey().getRoutingKey());
                hierarchicalOperationWorker.stop();
            } catch (Exception e) {
                log.error("Unable to stop worker: {}", hierarchicalOperationWorker);
                val errorMessage = "Unable to stop worker: " + hierarchicalOperationWorker.getType();
                throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR, errorMessage, e);
            }
        });
    }

    public final long pendingMessagesCount() {
        val atomicLong = new AtomicLong(0l);
        worker.traverse(hierarchicalOperationWorker -> {
            atomicLong.getAndAdd(hierarchicalOperationWorker.pendingMessagesCount());
        });
        return atomicLong.get();
    }

    public final long pendingSidelineMessagesCount() {
        val atomicLong = new AtomicLong(0l);
        worker.traverse(hierarchicalOperationWorker -> {
            atomicLong.getAndAdd(hierarchicalOperationWorker.pendingSidelineMessagesCount());
        });
        return atomicLong.get();
    }

    public final void publishWithDelay(final HierarchicalRoutingKey<String> routingKey,
                                       final Message message,
                                       final long delayMilliseconds) throws Exception {
        publishActor(routingKey).publishWithDelay(message, delayMilliseconds);
    }

    public final void publishWithExpiry(final HierarchicalRoutingKey<String> routingKey,
                                        final Message message,
                                        final long expiryInMs) throws Exception {
        publishActor(routingKey).publishWithExpiry(message, expiryInMs);
    }

    public final void publish(final HierarchicalRoutingKey<String> routingKey,
                              final Message message) throws Exception {
        publishActor(routingKey).publish(message);
    }

    public final void publish(final HierarchicalRoutingKey<String> routingKey,
                              final Message message,
                              final AMQP.BasicProperties properties) throws Exception {
        publishActor(routingKey).publish(message, properties);
    }

    private HierarchicalOperationWorker<MessageType, Message> publishActor(final HierarchicalRoutingKey<String> routingKey) {
        return (HierarchicalOperationWorker<MessageType, Message>) this.worker.get(messageType, routingKey);
    }

    private void initializeRouter() {
        this.worker = new HierarchicalDataStoreSupplierTree<>(
                messageType,
                hierarchicalTreeConfig,
                HierarchicalRouterUtils.actorConfigToWorkerConfigFunc,
                (routingKey, messageTypeKey, workerConfig) -> new HierarchicalOperationWorker<>(
                        messageType,
                        workerConfig,
                        hierarchicalTreeConfig.getDefaultData(),
                        routingKey,
                        connectionRegistry,
                        mapper,
                        retryStrategyFactory,
                        exceptionHandlingFactory,
                        clazz,
                        droppedExceptionTypes,
                        handlerFunction,
                        expiredMessageHandlingFunction)
        );
    }
}
