package io.appform.dropwizard.actors.router;

import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.common.ErrorCode;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.router.config.HierarchicalOperationWorkerConfig;
import io.appform.dropwizard.actors.router.tree.HierarchicalDataStoreSupplierTree;
import io.appform.dropwizard.actors.router.tree.HierarchicalTreeConfig;
import io.appform.dropwizard.actors.router.tree.key.HierarchicalRoutingKey;
import io.appform.dropwizard.actors.router.tree.key.RoutingKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@SuppressWarnings({"java:S119"})
public abstract class HierarchicalOperationRouter<MessageType extends Enum<MessageType>, Message> implements HierarchicalMessageRouter<Message> {

    private final MessageType messageType;
    private final HierarchicalTreeConfig<ActorConfig, String, HierarchicalOperationWorkerConfig> hierarchicalTreeConfig;

    @Getter
    private HierarchicalDataStoreSupplierTree<
            HierarchicalOperationWorkerConfig,
            ActorConfig,
            MessageType,
            HierarchicalOperationWorker<MessageType, ? extends Message>> workers;


    public HierarchicalOperationRouter(final MessageType messageType,
                                       final HierarchicalTreeConfig<ActorConfig, String, HierarchicalOperationWorkerConfig> hierarchicalTreeConfig) {
        this.messageType = messageType;
        this.hierarchicalTreeConfig = hierarchicalTreeConfig;
    }

    private void initializeRouter() {
        this.workers = new HierarchicalDataStoreSupplierTree<>(messageType, hierarchicalTreeConfig,
                HierarchicalRouterUtils.actorConfigToWorkerConfigFunc,
                (routingKey, messageTypeKey, workerConfig) -> getHierarchicalOperationWorker(routingKey, messageTypeKey, workerConfig, hierarchicalTreeConfig.getDefaultData()));
    }


    @Override
    public void start() {
        log.info("Initializing Router : {}", messageType);
        initializeRouter();
        log.info("Initialized Router : {}", messageType);

        log.info("Staring all workers");
        workers.traverse(hierarchicalOperationWorker -> {
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
    public void stop() {
        log.info("Stopping all workers");
        workers.traverse(hierarchicalOperationWorker -> {
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
            HierarchicalOperationWorker<MessageType, Message> worker = (HierarchicalOperationWorker<MessageType, Message>) workers.get(messageType, routingKey);
            log.info("Publishing message:{} to worker: {} ({})", message,
                    worker.getClass().getSimpleName(), worker.getRoutingKey().getRoutingKey());
            worker.publish(message);
        } catch (Exception e) {
            log.error("Unable to submit message to worker : {} {}", routingKey, message);
            val errorMessage = "Unable to submit message to worker " + routingKey;
            throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR, errorMessage, e);
        }
    }


    public abstract HierarchicalOperationWorker<MessageType, ? extends Message> getHierarchicalOperationWorker(final RoutingKey routingKey,
                                                                                                               final MessageType messageTypeKey,
                                                                                                               final HierarchicalOperationWorkerConfig workerConfig,
                                                                                                               final ActorConfig defaultActorConfig);
}
