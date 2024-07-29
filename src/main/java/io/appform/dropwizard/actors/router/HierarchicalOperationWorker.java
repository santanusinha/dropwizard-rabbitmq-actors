package io.appform.dropwizard.actors.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.router.config.HierarchicalOperationWorkerConfig;
import io.appform.dropwizard.actors.router.tree.key.RoutingKey;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.Set;
import java.util.function.Consumer;

@Getter
@SuppressWarnings({"java:S119"})
public class HierarchicalOperationWorker<MessageType extends Enum<MessageType>, Message>
        extends Actor<MessageType, Message> {

    private final RoutingKey routingKey;
    private final Consumer<Message> messageConsumer;

    protected HierarchicalOperationWorker(final MessageType messageType,
                                          final HierarchicalOperationWorkerConfig workerConfig,
                                          final ActorConfig actorConfig,
                                          final RoutingKey routingKey,
                                          final ConnectionRegistry connectionRegistry,
                                          final ObjectMapper mapper,
                                          final RetryStrategyFactory retryStrategyFactory,
                                          final ExceptionHandlingFactory exceptionHandlingFactory,
                                          final Class<? extends Message> clazz,
                                          final Set<Class<?>> droppedExceptionTypes,
                                          final Consumer<Message> messageConsumer) {
        super(messageType,
                HierarchicalRouterUtils.toActorConfig(messageType, routingKey, workerConfig, actorConfig),
                connectionRegistry,
                mapper,
                retryStrategyFactory,
                exceptionHandlingFactory,
                clazz,
                droppedExceptionTypes);
        this.routingKey = routingKey;
        this.messageConsumer = messageConsumer;
    }

    @Override
    protected boolean handle(Message message, MessageMetadata messageMetadata) {
        return process(message);
    }

    @Override
    protected boolean handle(Message message) {
        return process(message);
    }

    private boolean process(Message message) {
        try {
            messageConsumer.accept(message);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
