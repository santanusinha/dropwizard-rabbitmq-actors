/*
 * Copyright (c) 2024 Santanu Sinha <santanu.sinha@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.appform.dropwizard.actors.connectivity.actor;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.base.RandomShardIdCalculator;
import io.appform.dropwizard.actors.base.ShardIdCalculator;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.exceptionhandler.config.DropConfig;
import io.appform.dropwizard.actors.junit.extension.RabbitMQExtension;
import io.appform.dropwizard.actors.metrics.RMQMetricObserver;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.utils.RMQTestUtils;
import io.appform.dropwizard.actors.utils.CustomShardingTestActor;
import io.appform.dropwizard.actors.utils.TestShardedMessage;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */

@ExtendWith(RabbitMQExtension.class)
public class MessageBasedShardingTest {
    private static RMQConnection connection;
    private static final Environment environment = mock(Environment.class);
    private static final MetricRegistry METRIC_REGISTRY = SharedMetricRegistries.getOrCreate("test");
    private static RMQConfig RMQ_CONFIG;

    @BeforeEach
    @SneakyThrows
    void setup(final RabbitMQContainer rabbitMQContainer) {

        RMQ_CONFIG = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        when(environment.metrics()).thenReturn(METRIC_REGISTRY);
        when(environment.healthChecks()).thenReturn(mock(HealthCheckRegistry.class));

        connection = new RMQConnection("test-conn",
                                       RMQ_CONFIG,
                                       Executors.newSingleThreadExecutor(),
                                       environment,
                                       TtlConfig.builder().build(),
                                       new RMQMetricObserver(RMQ_CONFIG, METRIC_REGISTRY));
        connection.start();
    }

    @AfterEach
    @SneakyThrows
    void teardown() {
        connection.stop();
    }

    public static Stream<Arguments> shardIdGenerators() {
        return Stream.of(Arguments.arguments((ShardIdCalculator<TestShardedMessage>) TestShardedMessage::getShardId),
                         Arguments.arguments(new RandomShardIdCalculator<>(new ActorConfig().setShardCount(5))));
    }


    @ParameterizedTest
    @MethodSource("shardIdGenerators")
    @SneakyThrows
    void testSharding(ShardIdCalculator<TestShardedMessage> shardIdCalculator) {
        val objectMapper = new ObjectMapper();

        val exchange = "sharding-test-exchange-1-" + Math.abs(shardIdCalculator.hashCode());
        val actorConfig = new ActorConfig()
                .setExchange(exchange)
                .setShardCount(5)
                .setExceptionHandlerConfig(new DropConfig())
                .setConcurrency(5);
        val registry = new ConnectionRegistry(environment,
                                              (name, coreSize) -> Executors.newFixedThreadPool(1),
                                              RMQ_CONFIG,
                                              TtlConfig.builder().build(),
                                              new RMQMetricObserver(RMQ_CONFIG, METRIC_REGISTRY));
        val counters = new ConcurrentHashMap<Integer, AtomicInteger>();
        val actor = new CustomShardingTestActor(actorConfig, registry, objectMapper,
                                                shardIdCalculator,
                                                new RetryStrategyFactory(), new ExceptionHandlingFactory(), counters);
        actor.start();
        val originalTotalCount = new AtomicInteger();
        IntStream.range(0, 5)
                .forEach(shardId -> IntStream.range(0, 100).forEach(j -> {
                    try {
                        actor.publish(new TestShardedMessage(shardId));
                        originalTotalCount.incrementAndGet();
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> {
                    val sum = counters.values().stream().mapToInt(AtomicInteger::get).sum();
                    return sum == originalTotalCount.get();
                });
        assertEquals(5, counters.size());
        counters.values().forEach(sum -> assertEquals(100, sum.get()));
        actor.stop();
        try (val channel = connection.newChannel()) {
            IntStream.range(0,5)
                            .forEach(i -> {
                                try {
                                    channel.queueDelete("rabbitmq.actors.SHARDED_MESSAGE_" + i);
                                }
                                catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });

            channel.queueDelete("rabbitmq.actors.SHARDED_MESSAGE_SIDELINE");
            channel.exchangeDelete(exchange);
            channel.exchangeDelete(exchange + "_SIDELINE");
        }
    }

}
