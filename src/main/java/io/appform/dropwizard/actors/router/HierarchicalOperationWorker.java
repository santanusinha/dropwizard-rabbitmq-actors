package io.appform.dropwizard.actors.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.router.tree.key.RoutingKey;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Getter;

import java.util.Set;

@SuppressWarnings({"java:S119"})
public abstract class HierarchicalOperationWorker<MessageType extends Enum<MessageType>, Message>
        extends Actor<MessageType, Message> {

    @Getter
    private final RoutingKey routingKey;

    protected HierarchicalOperationWorker(final MessageType messageType,
                                          final ActorConfig workerConfig,
                                          final RoutingKey routingKey,
                                          final ConnectionRegistry connectionRegistry,
                                          final ObjectMapper mapper,
                                          final RetryStrategyFactory retryStrategyFactory,
                                          final ExceptionHandlingFactory exceptionHandlingFactory,
                                          final Class<? extends Message> clazz,
                                          final Set<Class<?>> droppedExceptionTypes) {
        super(messageType,
                HierarchicalRouterUtils.toActorConfig(messageType, routingKey, workerConfig),
                connectionRegistry,
                mapper,
                retryStrategyFactory,
                exceptionHandlingFactory,
                clazz,
                droppedExceptionTypes);
        this.routingKey = routingKey;
    }

    @Override
    protected boolean handle(final Message m,
                             final MessageMetadata messageMetadata) {
        boolean result;
        try {
            result = process(m);
        } catch (RabbitmqActorException se) {
            return false;
        }

        return result;
    }

    protected abstract boolean process(final Message m);

    @VisibleForTesting
    public boolean processIT(final Message m) {
        return process(m);
    }

}
