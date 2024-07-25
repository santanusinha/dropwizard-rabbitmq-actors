package io.appform.dropwizard.actors.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.router.config.HierarchicalOperationWorkerConfig;
import io.appform.dropwizard.actors.router.tree.key.RoutingKey;
import lombok.Getter;

import java.util.Set;

@SuppressWarnings({"java:S119"})
public abstract class HierarchicalOperationWorker<MessageType extends Enum<MessageType>, Message>
        extends Actor<MessageType, Message> {

    @Getter
    private final RoutingKey routingKey;

    protected HierarchicalOperationWorker(final MessageType messageType,
                                          final HierarchicalOperationWorkerConfig workerConfig,
                                          final ActorConfig actorConfig,
                                          final RoutingKey routingKey,
                                          final ConnectionRegistry connectionRegistry,
                                          final ObjectMapper mapper,
                                          final RetryStrategyFactory retryStrategyFactory,
                                          final ExceptionHandlingFactory exceptionHandlingFactory,
                                          final Class<? extends Message> clazz,
                                          final Set<Class<?>> droppedExceptionTypes) {
        super(messageType,
                HierarchicalRouterUtils.toActorConfig(messageType, routingKey, workerConfig, actorConfig),
                connectionRegistry,
                mapper,
                retryStrategyFactory,
                exceptionHandlingFactory,
                clazz,
                droppedExceptionTypes);
        this.routingKey = routingKey;
    }

}
