package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Slf4j
public abstract class HierarchicalActor<MessageType extends Enum<MessageType>, Message> extends HierarchicalBaseActor<MessageType, Message> {

    private MessageType type;

    protected HierarchicalActor(
            MessageType type,
            HierarchicalActorConfig config,
            ConnectionRegistry connectionRegistry,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            ExceptionHandlingFactory exceptionHandlingFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        super(type, config, connectionRegistry, mapper, retryStrategyFactory, exceptionHandlingFactory,
                clazz, droppedExceptionTypes);
        this.type = type;
    }

}
