package io.appform.dropwizard.actors.failurehandler.strategy.testactor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.failurehandler.handlers.FailureHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.utils.ActorType;
import io.appform.dropwizard.actors.utils.TestMessage;

import java.util.Set;

public class TestActor2 extends Actor<ActorType, TestMessage> {

    protected TestActor2(final ActorConfig config,
                         final ConnectionRegistry connectionRegistry,
                         final ObjectMapper mapper,
                         final RetryStrategyFactory retryStrategyFactory,
                         final ExceptionHandlingFactory exceptionHandlingFactory, Class<? extends TestMessage> clazz,
                         final Set<Class<?>> droppedExceptionTypes) {
        super(ActorType.Actor_Type_2, config, connectionRegistry, mapper, retryStrategyFactory, exceptionHandlingFactory,
                clazz, droppedExceptionTypes);
    }

    protected TestActor2(final ActorConfig config,
                         final ConnectionRegistry connectionRegistry,
                         final ObjectMapper mapper,
                         final RetryStrategyFactory retryStrategyFactory,
                         final ExceptionHandlingFactory exceptionHandlingFactory,
                         final FailureHandlingFactory failureHandlingFactory,
                         final Class<? extends TestMessage> clazz,
                         final Set<Class<?>> droppedExceptionTypes) {
        super(ActorType.Actor_Type_2, config, connectionRegistry, mapper, retryStrategyFactory,
                exceptionHandlingFactory, failureHandlingFactory, clazz, droppedExceptionTypes);
    }

    @Override
    protected boolean handle(final TestMessage testMessage, final MessageMetadata messageMetadata) {
        return true;
    }
}
