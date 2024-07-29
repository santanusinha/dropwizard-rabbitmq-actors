package io.appform.dropwizard.actors.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.common.ErrorCode;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.router.config.HierarchicalOperationWorkerConfig;
import io.appform.dropwizard.actors.router.tree.HierarchicalDataStoreSupplierTree;
import io.appform.dropwizard.actors.router.tree.HierarchicalTreeConfig;
import io.appform.dropwizard.actors.router.tree.key.HierarchicalRoutingKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Set;

@Slf4j
@SuppressWarnings({"java:S119"})
public abstract class HierarchicalOperationRouter<MessageType extends Enum<MessageType>, Message> implements HierarchicalMessageRouter<Message> {

    private final MessageType messageType;
    private final HierarchicalTreeConfig<ActorConfig, String, HierarchicalOperationWorkerConfig> hierarchicalTreeConfig;
    private final ConnectionRegistry connectionRegistry;
    private final ObjectMapper mapper;
    private final RetryStrategyFactory retryStrategyFactory;
    private final ExceptionHandlingFactory exceptionHandlingFactory;
    private final Class<? extends Message> clazz;
    private final Set<Class<?>> droppedExceptionTypes;

    @Getter
    private HierarchicalDataStoreSupplierTree<
            HierarchicalOperationWorkerConfig,
            ActorConfig,
            MessageType,
            HierarchicalOperationWorker<MessageType, ? extends Message>> worker;

    protected HierarchicalOperationRouter(final MessageType messageType,
                                          final HierarchicalTreeConfig<ActorConfig, String, HierarchicalOperationWorkerConfig> hierarchicalTreeConfig,
                                          final ConnectionRegistry connectionRegistry,
                                          final ObjectMapper mapper,
                                          final RetryStrategyFactory retryStrategyFactory,
                                          final ExceptionHandlingFactory exceptionHandlingFactory,
                                          final Class<? extends Message> clazz,
                                          final Set<Class<?>> droppedExceptionTypes) {
        this.messageType = messageType;
        this.hierarchicalTreeConfig = hierarchicalTreeConfig;
        this.connectionRegistry = connectionRegistry;
        this.mapper = mapper;
        this.retryStrategyFactory = retryStrategyFactory;
        this.exceptionHandlingFactory = exceptionHandlingFactory;
        this.clazz = clazz;
        this.droppedExceptionTypes = droppedExceptionTypes;
    }

    public void initializeRouter() {
        this.worker = new HierarchicalDataStoreSupplierTree<>(
                messageType,
                hierarchicalTreeConfig,
                HierarchicalRouterUtils.actorConfigToWorkerConfigFunc,
                (routingKey, messageTypeKey, workerConfig) ->
                        new HierarchicalOperationWorker<MessageType, Message>(
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
                                this::process)
        );
    }
    

    @Override
    public void start() throws Exception {
        log.info("Initializing Router");
        initializeRouter();
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

    @Override
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

    @Override
    public void submit(final HierarchicalRoutingKey<String> routingKey,
                       final Message message) {
        try {
            HierarchicalOperationWorker<MessageType, Message> worker = (HierarchicalOperationWorker<MessageType, Message>) this.worker.get(messageType, routingKey);
            log.info("Publishing message:{} to worker: {} ({})", message,
                    worker.getClass().getSimpleName(), worker.getRoutingKey().getRoutingKey());
            worker.publish(message);
        } catch (Exception e) {
            log.error("Unable to submit message to worker : {} {}", routingKey, message);
            val errorMessage = "Unable to submit message to worker " + routingKey;
            throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR, errorMessage, e);
        }
    }

    public abstract boolean process(final Message message);

}
