package io.dropwizard.actors.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MessageProperties;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.retry.RetryStrategyFactory;
import io.dropwizard.lifecycle.Managed;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;

import java.util.Collections;
import java.util.Set;

/**
 * This is a managed wrapper for {@link UnmanagedBaseActor} this is managed and therefore started by D/W.
 */
@Data
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class BaseActor<Message> implements Managed {

    private final UnmanagedBaseActor<Message> actorImpl;
    private final Set<Class<?>> droppedExceptionTypes;

    protected BaseActor(
            String name,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        this.droppedExceptionTypes
                = null == droppedExceptionTypes
                    ? Collections.emptySet() : droppedExceptionTypes;
        actorImpl = new UnmanagedBaseActor<>(name, config, connection, mapper, retryStrategyFactory, clazz, this::handle, this::isExceptionIgnorable);
    }

    abstract protected boolean handle(Message message) throws Exception;

    protected boolean isExceptionIgnorable(Throwable t) {
        return droppedExceptionTypes
                .stream()
                .anyMatch(exceptionType -> ClassUtils.isAssignable(t.getClass(), exceptionType));
    }

    public final void publishWithDelay(Message message, long delayMilliseconds) throws Exception {
        actorImpl.publishWithDelay(message, delayMilliseconds);
    }

    public final void publish(Message message) throws Exception {
        publish(message, MessageProperties.MINIMAL_PERSISTENT_BASIC);
    }

    public final void publish(Message message, AMQP.BasicProperties properties) throws Exception {
        actorImpl.publish(message, properties);
    }

    @Override
    public void start() throws Exception {
        actorImpl.start();

    }

    @Override
    public void stop() throws Exception {
        actorImpl.start();
    }

}
