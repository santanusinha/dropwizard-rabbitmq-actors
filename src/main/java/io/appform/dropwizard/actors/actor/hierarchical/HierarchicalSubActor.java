package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.RoutingKey;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Set;

@Getter
@EqualsAndHashCode
@SuppressWarnings({"java:S119", "java:S107"})
public class HierarchicalSubActor<MessageType extends Enum<MessageType>, Message>
        extends Actor<MessageType, Message> {

    private final RoutingKey routingKey;
    private final MessageHandlingFunction<Message, Boolean> handlerFunction;
    private final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction;

    public HierarchicalSubActor(final MessageType messageType,
                                final HierarchicalSubActorConfig subActorConfig,
                                final HierarchicalActorConfig hierarchicalActorConfig,
                                final RoutingKey routingKey,
                                final ConnectionRegistry connectionRegistry,
                                final ObjectMapper mapper,
                                final RetryStrategyFactory retryStrategyFactory,
                                final ExceptionHandlingFactory exceptionHandlingFactory,
                                final Class<? extends Message> clazz,
                                final Set<Class<?>> droppedExceptionTypes,
                                final MessageHandlingFunction<Message, Boolean> handlerFunction,
                                final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction) {
        super(messageType,
                HierarchicalRouterUtils.toActorConfig(messageType, routingKey, subActorConfig, hierarchicalActorConfig),
                connectionRegistry,
                mapper,
                retryStrategyFactory,
                exceptionHandlingFactory,
                clazz,
                droppedExceptionTypes);
        this.routingKey = routingKey;
        this.handlerFunction = handlerFunction;
        this.expiredMessageHandlingFunction = expiredMessageHandlingFunction;
    }

    @Override
    protected boolean handle(Message message, MessageMetadata messageMetadata) throws Exception {
        return handlerFunction.apply(message, messageMetadata);
    }

    @Override
    protected boolean handleExpiredMessages(Message message, MessageMetadata messageMetadata) throws Exception {
        return expiredMessageHandlingFunction.apply(message, messageMetadata);
    }

}
