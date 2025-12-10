package io.appform.dropwizard.actors.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import java.util.HashSet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SidelineProcessorTestActor extends Actor<ActorType, TestMessage> {

    @Getter
    private int handleCalledCount = 0;

    @Getter
    private int handleSidelineProcessorCalledCount = 0;

    public SidelineProcessorTestActor(ActorConfig config, ConnectionRegistry connectionRegistry, ObjectMapper mapper,
                                      RetryStrategyFactory retryStrategyFactory,
                                      ExceptionHandlingFactory exceptionHandlingFactory) {
        super(ActorType.ALWAYS_FAIL_ACTOR, config, connectionRegistry, mapper,
                retryStrategyFactory, exceptionHandlingFactory, TestMessage.class, new HashSet<>());
    }

    @Override
    protected boolean handle(TestMessage message, MessageMetadata messageMetadata) {
        handleCalledCount++;
        log.info("Main Queue handle message {}", message);
        return false;
    }

    @Override
    protected boolean handleSidelineProcessor(TestMessage message,
                                              MessageMetadata messageMetadata) {
        handleSidelineProcessorCalledCount++;
        log.info("Sideline Processor handle message {}", message);

        if(handleSidelineProcessorCalledCount == message.getSidelineProcessorQueueHandleSuccessCount())
            return true;
        else {
            throw new RuntimeException("Sideline Processor handle message failed");
        }
    }
}
