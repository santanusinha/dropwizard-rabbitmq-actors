package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.actor.UnmanagedBaseActor;
import io.appform.dropwizard.actors.base.RandomShardIdCalculator;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.connectivity.strategy.DefaultConnectionStrategy;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.junit.extension.RabbitMQExtension;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.WaitMode;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.utils.RMQTestUtils;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-broker integration test for the async retry flow: an {@link io.appform.dropwizard.actors.actor.UnmanagedBaseActor}
 * with ASYNC retry hands failed deliveries to {@link io.appform.dropwizard.actors.retry.async.TtlRequeueRetryScheduler}
 * via {@link io.appform.dropwizard.actors.retry.async.AsyncRetryer}; attempts are persisted on the wire in the
 * x-actor-retry-* headers and exhausted budgets sideline the original message.
 */
@Slf4j
@ExtendWith(RabbitMQExtension.class)
public class AsyncRetryIntegrationTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleTestApplication.class);

    private static final String EXCHANGE = "async-retry-it-exchange";
    private static final String PREFIX = "async.retry.it";
    private static final String RETRY_QUEUE_HEADER = "x-actor-retry-queue";
    private static final String RETRY_ATTEMPT_HEADER = "x-actor-retry-attempt";
    private static final String RETRY_STARTED_AT_HEADER = "x-actor-retry-started-at";

    private RMQConnection connection;
    private final ObjectMapper mapper = new ObjectMapper();

    static class TestMessage {
        public TestMessage() {
        }

        public TestMessage(String id) {
            this.id = id;
        }

        public String id;
    }

    @BeforeAll
    @SneakyThrows
    static void beforeAll() {
        System.setProperty("dw." + "server.applicationConnectors[0].port", "0");
        System.setProperty("dw." + "server.adminConnectors[0].port", "0");
        app.before();
    }

    @BeforeEach
    void beforeEach(final RabbitMQContainer rabbitMQContainer) throws Exception {
        val config = RMQTestUtils.getRMQConfig(rabbitMQContainer);
        connection = new RMQConnection("async-retry-it-conn", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build(),
                new TerminalRMQObserver());
        connection.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.stop();
    }

    @AfterAll
    @SneakyThrows
    static void afterAll() {
        app.after();
    }

    private CountLimitedFixedWaitRetryConfig asyncRetry(int maxAttempts, long waitMs) {
        val retry = new CountLimitedFixedWaitRetryConfig();
        retry.setWaitMode(WaitMode.ASYNC);
        retry.setMaxAttempts(maxAttempts);
        retry.setWaitTime(Duration.milliseconds(waitMs));
        return retry;
    }

    private UnmanagedBaseActor<TestMessage> startActor(String name,
                                                        CountLimitedFixedWaitRetryConfig retry,
                                                        MessageHandlingFunction<TestMessage, Boolean> handler) throws Exception {
        val config = ActorConfig.builder()
                .exchange(EXCHANGE)
                .prefix(PREFIX)
                .concurrency(1)
                .prefetchCount(1)
                .retryConfig(retry)
                .consumer(ConsumerConfig.builder()
                        .connectionIsolationStrategy(new DefaultConnectionStrategy())
                        .tagPrefix("main")
                        .build())
                .build();
        val actor = new UnmanagedBaseActor<>(name, config, connection, connection, connection, mapper,
                new RandomShardIdCalculator<>(config), new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                TestMessage.class, handler,
                (message, metadata) -> true,
                (message, metadata) -> true,
                (message, metadata) -> true,
                error -> false);
        actor.start();
        return actor;
    }

    /**
     * A failed delivery is redelivered by the broker only after the configured wait: the delay between the
     * first attempt and the redelivery matches the retry wait, and the redelivered message succeeds and is acked.
     */
    @Test
    void asyncFailureIsRedeliveredAfterTheConfiguredDelayAndThenSucceeds() throws Exception {
        val deliveryNanos = new CopyOnWriteArrayList<Long>();
        val deliveryIds = new CopyOnWriteArrayList<String>();
        val actor = startActor("success-actor", asyncRetry(3, 400), (msg, meta) -> {
            deliveryNanos.add(System.nanoTime());
            deliveryIds.add(msg.id);
            if (deliveryNanos.size() == 1) {
                throw new IllegalStateException("fail first attempt");
            }
            return true;
        });
        try {
            actor.publish(new TestMessage("m-1"));
            await().atMost(20, TimeUnit.SECONDS).until(() -> deliveryNanos.size() == 2);

            val delayMs = TimeUnit.NANOSECONDS.toMillis(deliveryNanos.get(1) - deliveryNanos.get(0));
            assertTrue(delayMs >= 300, "redelivery happened before the retry wait: " + delayMs + "ms");
            assertTrue(delayMs <= 5000, "redelivery took too long: " + delayMs + "ms");
            assertEquals(List.of("m-1", "m-1"), deliveryIds);

            await().atMost(5, TimeUnit.SECONDS).until(() -> actor.pendingMessagesCount() == 0
                    && actor.pendingSidelineMessagesCount() == 0);
        } finally {
            actor.stop();
        }
    }

    /**
     * The attempt budget persists across the wire in the x-actor-retry-* headers (LongString/Long values on
     * redelivery); once exhausted, the original payload and headers are sidelined unchanged.
     */
    @Test
    void attemptBudgetSurvivesTheWireAndExhaustionSidelinesOriginalPayloadAndHeaders() throws Exception {
        val headersByAttempt = new CopyOnWriteArrayList<Map<String, Object>>();
        val actor = startActor("exhaust-actor", asyncRetry(2, 300), (msg, meta) -> {
            headersByAttempt.add(meta.getHeaders());
            throw new IllegalStateException("always fails");
        });
        val queue = NamingUtils.queueName(PREFIX, "exhaust-actor");
        try {
            val properties = new AMQP.BasicProperties.Builder()
                    .deliveryMode(2)
                    .headers(Map.of("custom-header", "custom-value"))
                    .build();
            val message = new TestMessage("m-2");
            actor.publish(message, properties);

            await().atMost(20, TimeUnit.SECONDS).until(() -> headersByAttempt.size() == 2);

            val first = headersByAttempt.get(0);
            assertNull(first.get(RETRY_QUEUE_HEADER));
            assertNull(first.get(RETRY_ATTEMPT_HEADER));

            val second = headersByAttempt.get(1);
            assertEquals(queue, String.valueOf(second.get(RETRY_QUEUE_HEADER)));
            assertEquals(2L, ((Number) second.get(RETRY_ATTEMPT_HEADER)).longValue());
            val startedAt = ((Number) second.get(RETRY_STARTED_AT_HEADER)).longValue();
            assertTrue(startedAt > 0 && startedAt <= System.currentTimeMillis());
            assertEquals("custom-value", String.valueOf(second.get("custom-header")));

            try (val channel = connection.newChannel()) {
                val sidelined = await().atMost(10, TimeUnit.SECONDS)
                        .ignoreExceptions()
                        .until(() -> channel.basicGet(NamingUtils.getSideline(queue), true), Objects::nonNull);
                assertArrayEquals(mapper.writeValueAsBytes(message), sidelined.getBody());
                val sidelinedHeaders = sidelined.getProps().getHeaders();
                assertEquals("custom-value", String.valueOf(sidelinedHeaders.get("custom-header")));
                assertEquals(queue, String.valueOf(sidelinedHeaders.get(RETRY_QUEUE_HEADER)));
                assertEquals(2L, ((Number) sidelinedHeaders.get(RETRY_ATTEMPT_HEADER)).longValue());
            }
        } finally {
            actor.stop();
        }
    }

    /**
     * Handing a failure to the broker does not block the consumer: another message is handled while the failed
     * one waits in its TTL retry queue, and the scheduled retry still fires after its delay.
     */
    @Test
    void retryHandoffDoesNotBlockOtherMessages() throws Exception {
        val blockedAttempts = new AtomicInteger();
        val okHandled = new AtomicInteger();
        val firstBlockedNanos = new AtomicLong(-1);
        val actor = startActor("nonblock-actor", asyncRetry(3, 2000), (msg, meta) -> {
            if ("blocked".equals(msg.id)) {
                firstBlockedNanos.compareAndSet(-1, System.nanoTime());
                blockedAttempts.incrementAndGet();
                throw new IllegalStateException("blocked fails");
            }
            okHandled.incrementAndGet();
            return true;
        });
        try {
            actor.publish(new TestMessage("blocked"));
            await().atMost(15, TimeUnit.SECONDS).until(() -> blockedAttempts.get() == 1);

            actor.publish(new TestMessage("ok"));
            await().atMost(3, TimeUnit.SECONDS).until(() -> okHandled.get() == 1);
            assertEquals(1, blockedAttempts.get(), "blocked message must still be waiting in its TTL retry queue");

            await().atMost(15, TimeUnit.SECONDS).until(() -> blockedAttempts.get() == 2);
            val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - firstBlockedNanos.get());
            assertTrue(waitedMs >= 1900, "second attempt fired before the configured retry wait: " + waitedMs + "ms");
            assertEquals(1, okHandled.get());
        } finally {
            actor.stop();
        }
    }
}
