package io.appform.dropwizard.actors.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;

@Slf4j
public class SidelineTestActor extends Actor<ActorType, TestMessage> {
    public SidelineTestActor(ActorConfig config, ConnectionRegistry connectionRegistry, ObjectMapper mapper,
                                RetryStrategyFactory retryStrategyFactory,
                                ExceptionHandlingFactory exceptionHandlingFactory) {
        super(ActorType.ALWAYS_FAIL_ACTOR, config, connectionRegistry, mapper,
                retryStrategyFactory, exceptionHandlingFactory, TestMessage.class, new HashSet<>());
    }

    @Override
    protected boolean handle(TestMessage message, MessageMetadata messageMetadata) {
        log.error("Failed to handle message {}", message);
        return false;
    }
}
