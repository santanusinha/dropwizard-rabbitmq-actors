package io.dropwizard.actors.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * A simpler derivation of {@link BaseActor} to be used in most common actor use cases. This is managed by dropwizard.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Slf4j
public abstract class  Actor<MessageType  extends Enum<MessageType>, Message> extends BaseActor<Message> {

    protected Actor(
            MessageType type,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        super(type.name(), config, connection, mapper, retryStrategyFactory, clazz, droppedExceptionTypes);
    }


}
