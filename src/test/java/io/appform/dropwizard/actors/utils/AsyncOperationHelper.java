package io.appform.dropwizard.actors.utils;

import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.ProducerConfig;
import io.appform.dropwizard.actors.actor.SidelineProcessorConfig;
import io.appform.dropwizard.actors.connectivity.strategy.SharedConnectionStrategy;
import io.appform.dropwizard.actors.exceptionhandler.config.DropConfig;
import io.appform.dropwizard.actors.retry.config.CountLimitedExponentialWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.dropwizard.util.Duration;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class AsyncOperationHelper {
    public static ActorConfig buildActorConfig() {
        List<String> routingKey = new ArrayList<>();
        routingKey.add("testsuite");
        routingKey.add("first");
        return ActorConfig.builder()
                .exchange("test.exchange")
                .prefix("test")
                .concurrency(2)
                .prefetchCount(1)
                .shardCount(2)
                .retryConfig(CountLimitedExponentialWaitRetryConfig.builder()
                        .maxAttempts(1)
                        .multipier(50)
                        .maxTimeBetweenRetries(Duration.seconds(30))
                        .build())
                .exceptionHandlerConfig(new DropConfig())
                .producer(ProducerConfig.builder()
                        .connectionIsolationStrategy(SharedConnectionStrategy.builder()
                                .name(String.join("_", "p", String.join("_", routingKey).toLowerCase()))
                                .build())
                        .build())
                .consumer(ConsumerConfig.builder()
                        .connectionIsolationStrategy(SharedConnectionStrategy.builder()
                                .name(String.join("_", "c", String.join("_", routingKey).toLowerCase()))
                                .build())
                        .build())
                .build();
    }

    public static ActorConfig buildActorConfigWithSidelineProcessorEnabled() {
        List<String> routingKey = new ArrayList<>();
        routingKey.add("testsuite");
        routingKey.add("first");
        return ActorConfig.builder()
                .exchange("test.exchange")
                .prefix("test")
                .concurrency(2)
                .prefetchCount(1)
                .retryConfig(CountLimitedExponentialWaitRetryConfig.builder()
                        .maxAttempts(1)
                        .multipier(50)
                        .maxTimeBetweenRetries(Duration.seconds(30))
                        .build())
                .sidelineProcessorConfig(SidelineProcessorConfig.builder()
                        .concurrency(2)
                        .consumerConfig(ConsumerConfig.builder()
                                .connectionIsolationStrategy(SharedConnectionStrategy.builder()
                                        .name(String.join("_", "s", String.join("_", routingKey).toLowerCase()))
                                        .build())
                                .build())
                        .build())
                .producer(ProducerConfig.builder()
                        .connectionIsolationStrategy(SharedConnectionStrategy.builder()
                                .name(String.join("_", "p", String.join("_", routingKey).toLowerCase()))
                                .build())
                        .build())
                .consumer(ConsumerConfig.builder()
                        .connectionIsolationStrategy(SharedConnectionStrategy.builder()
                                .name(String.join("_", "c", String.join("_", routingKey).toLowerCase()))
                                .build())
                        .build())
                .build();
    }
}
