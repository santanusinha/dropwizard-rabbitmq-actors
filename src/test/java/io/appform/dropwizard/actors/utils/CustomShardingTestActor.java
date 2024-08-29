package io.appform.dropwizard.actors.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.base.ShardIdCalculator;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CustomShardingTestActor extends Actor<TestShardedMessage.Type, TestShardedMessage> {
    private final Map<Integer, AtomicInteger> counter;

    public CustomShardingTestActor(ActorConfig config,
                                   ConnectionRegistry connectionRegistry,
                                   ObjectMapper mapper,
                                   ShardIdCalculator<TestShardedMessage> shardIdCalculator,
                                   RetryStrategyFactory retryStrategyFactory,
                                   ExceptionHandlingFactory exceptionHandlingFactory,
                                   Map<Integer, AtomicInteger> counter) {
        super(TestShardedMessage.Type.SHARDED_MESSAGE,
              config,
              connectionRegistry,
              mapper,
              shardIdCalculator,
              retryStrategyFactory,
              exceptionHandlingFactory,
              TestShardedMessage.class,
              new HashSet<>());
        this.counter = counter;
    }

    @Override
    protected boolean handle(TestShardedMessage message, MessageMetadata messageMetadata) {
        counter.computeIfAbsent(message.getShardId(), shardId -> new AtomicInteger(0)).incrementAndGet();
        return true;
    }
}
