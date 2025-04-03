package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.HierarchicalRoutingKey;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.RoutingKey;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * This is a managed wrapper for {@link HierarchicalUnmanagedBaseActor} this is managed and therefore started by D/W.
 * *
 *
 * @param <MessageType> Enum which will be used to creating actors
 * @param <Message>  message param is data to be processed by consumer
 */
@Data
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class HierarchicalBaseActor<MessageType extends Enum<MessageType>, Message> implements IHierarchicalBaseActor<Message> {

    public static final RoutingKey EMPTY_ROUTING_KEY = RoutingKey.builder().build();
    private final HierarchicalUnmanagedBaseActor<MessageType, Message> actorImpl;

    protected HierarchicalBaseActor(
            MessageType messageType,
            HierarchicalActorConfig hierarchicalActorConfig,
            ConnectionRegistry connectionRegistry,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            ExceptionHandlingFactory exceptionHandlingFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        Set<Class<?>> droppedExceptionTypeSet = null == droppedExceptionTypes
                ? Collections.emptySet() : droppedExceptionTypes;
        actorImpl = new HierarchicalUnmanagedBaseActor<>(messageType, hierarchicalActorConfig, connectionRegistry, mapper, retryStrategyFactory,
                exceptionHandlingFactory, clazz, droppedExceptionTypeSet,
                this::handle,
                this::handleExpiredMessages);
    }

    /*
        Override this method in your code for custom implementation.
     */
    protected boolean handle(Message message, MessageMetadata messageMetadata) throws Exception {
        throw new UnsupportedOperationException("Implement this method");
    }

    /*
        Override this method in your code in case you want to handle the expired messages separately
     */
    protected boolean handleExpiredMessages(Message message, MessageMetadata messageMetadata) throws Exception {
        return true;
    }


    @Override
    public final void publishWithDelay(final Message message,
                                       final long delayMilliseconds) throws Exception {
        publishWithDelay(EMPTY_ROUTING_KEY, message, delayMilliseconds);
    }

    @Override
    public final void publishWithDelay(final HierarchicalRoutingKey<String> routingKey,
                                       final Message message,
                                       final long delayMilliseconds) throws Exception {
        actorImpl.publishWithDelay(routingKey, message, delayMilliseconds);
    }

    @Override
    public final void publishWithExpiry(final Message message, final long expiryInMs) throws Exception {
        publishWithExpiry(EMPTY_ROUTING_KEY, message, expiryInMs);
    }

    @Override
    public final void publishWithExpiry(final HierarchicalRoutingKey<String> routingKey,
                                        final Message message,
                                        final long expiryInMs) throws Exception {
        actorImpl.publishWithExpiry(routingKey, message, expiryInMs);
    }

    @Override
    public final void publish(final Message message) throws Exception {
        publish(EMPTY_ROUTING_KEY, message);
    }

    @Override
    public final void publish(final HierarchicalRoutingKey<String> routingKey,
                              final Message message) throws Exception {
        val properties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .timestamp(new Date())
                .build();
        publish(routingKey, message, properties);
    }

    @Override
    public final void publish(final Message message,
                              final AMQP.BasicProperties properties) throws Exception {
        publish(EMPTY_ROUTING_KEY, message, properties);
    }

    @Override
    public final void publish(final HierarchicalRoutingKey<String> routingKey,
                              final Message message,
                              final AMQP.BasicProperties properties) throws Exception {
        actorImpl.publish(routingKey, message, properties);
    }

    @Override
    public final long pendingMessagesCount() {
        return actorImpl.pendingMessagesCount();
    }


    @Override
    public final long pendingSidelineMessagesCount() {
        return actorImpl.pendingSidelineMessagesCount();
    }

    @Override
    public void start() throws Exception {
        actorImpl.start();
    }

    @Override
    public void stop() throws Exception {
        actorImpl.stop();
    }
}
