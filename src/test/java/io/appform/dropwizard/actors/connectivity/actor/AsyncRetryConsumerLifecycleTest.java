package io.appform.dropwizard.actors.connectivity.actor;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
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
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.WaitMode;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.utils.RMQTestUtils;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.util.Duration;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Real-broker regression for {@code UnmanagedConsumer}'s stop and recovery handling with ASYNC (TTL requeue) retries:
 * <ul>
 *   <li>stopping a consumer while a retry waits in its delay queue neither re-subscribes nor loses the message, and a
 *       restarted consumer processes it exactly once;</li>
 *   <li>a connection failure is left to automatic recovery: exactly the configured consumers come back (no extra
 *       re-created channel), and the pending retry is delivered once.</li>
 * </ul>
 */
@ExtendWith(RabbitMQExtension.class)
class AsyncRetryConsumerLifecycleTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleTestApplication.class);

    private static final String EXCHANGE = "async-retry-lc-exchange";
    private static final String PREFIX = "async.retry.lc";
    private static final String CONNECTION_NAME = "async-retry-lc-conn";

    private final ObjectMapper mapper = new ObjectMapper();
    private RabbitMQContainer broker;
    private RMQConnection connection;
    private Connection admin;

    static class TestMessage {
        public String id;

        public TestMessage() {
        }

        TestMessage(final String id) {
            this.id = id;
        }
    }

    @BeforeAll
    @SneakyThrows
    static void beforeAll() {
        System.setProperty("dw." + "server.applicationConnectors[0].port", "0");
        System.setProperty("dw." + "server.adminConnectors[0].port", "0");
        app.before();
    }

    @AfterAll
    @SneakyThrows
    static void afterAll() {
        app.after();
    }

    @BeforeEach
    void beforeEach(final RabbitMQContainer rabbitMQContainer) throws Exception {
        broker = rabbitMQContainer;
        connection = new RMQConnection(CONNECTION_NAME, RMQTestUtils.getRMQConfig(rabbitMQContainer),
                Executors.newFixedThreadPool(4), app.getEnvironment(), TtlConfig.builder().build(),
                new TerminalRMQObserver());
        connection.start();
        final ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(broker.getHost());
        factory.setPort(broker.getAmqpPort());
        factory.setUsername(broker.getAdminUsername());
        factory.setPassword(broker.getAdminPassword());
        admin = factory.newConnection("async-retry-lc-admin");
    }

    @AfterEach
    void afterEach() throws Exception {
        admin.close();
        connection.stop();
    }

    private UnmanagedBaseActor<TestMessage> startActor(final String name,
                                                       final int concurrency,
                                                       final long retryWaitMs,
                                                       final MessageHandlingFunction<TestMessage, Boolean> handler)
            throws Exception {
        final CountLimitedFixedWaitRetryConfig retry = new CountLimitedFixedWaitRetryConfig();
        retry.setWaitMode(WaitMode.ASYNC);
        retry.setMaxAttempts(3);
        retry.setWaitTime(Duration.milliseconds(retryWaitMs));
        final ActorConfig config = ActorConfig.builder()
                .exchange(EXCHANGE)
                .prefix(PREFIX)
                .concurrency(concurrency)
                .prefetchCount(1)
                .retryConfig(retry)
                .consumer(ConsumerConfig.builder().connectionIsolationStrategy(new DefaultConnectionStrategy()).build())
                .build();
        final UnmanagedBaseActor<TestMessage> actor = new UnmanagedBaseActor<>(name, config, connection, connection,
                connection, mapper, new RandomShardIdCalculator<>(config), new RetryStrategyFactory(),
                new ExceptionHandlingFactory(), TestMessage.class, handler,
                (message, metadata) -> true, (message, metadata) -> true, (message, metadata) -> true,
                error -> false);
        actor.start();
        return actor;
    }

    private int consumers(final String queue) throws Exception {
        try (Channel channel = admin.createChannel()) {
            return (int) channel.consumerCount(queue);
        }
    }

    private int messages(final String queue) throws Exception {
        try (Channel channel = admin.createChannel()) {
            return (int) channel.messageCount(queue);
        }
    }

    @Test
    void stopDuringPendingAsyncRetryKeepsTheMessageAndRestartProcessesItOnce() throws Exception {
        final String name = "lc-stop-actor";
        final String queue = NamingUtils.queueName(PREFIX, name);
        final AtomicInteger attempts = new AtomicInteger();
        final UnmanagedBaseActor<TestMessage> first = startActor(name, 1, 3_000, (message, metadata) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("fail; retry later");
        });
        first.publish(new TestMessage("m1"));
        await().atMost(15, TimeUnit.SECONDS).until(() -> attempts.get() == 1);

        // Stop while the retry waits in its delay queue: nothing re-subscribes, nothing is consumed.
        first.stop();
        await().atMost(5, TimeUnit.SECONDS).until(() -> consumers(queue) == 0);
        // After the delay the retry is dead-lettered back to the main queue and stays there.
        await().atMost(15, TimeUnit.SECONDS).until(() -> messages(queue) == 1);
        TimeUnit.SECONDS.sleep(1);
        assertEquals(0, consumers(queue));
        assertEquals(1, attempts.get());

        final AtomicInteger restartAttempts = new AtomicInteger();
        final UnmanagedBaseActor<TestMessage> restarted = startActor(name, 1, 3_000, (message, metadata) -> {
            restartAttempts.incrementAndGet();
            return true;
        });
        try {
            await().atMost(10, TimeUnit.SECONDS).until(() -> restartAttempts.get() == 1);
            TimeUnit.SECONDS.sleep(2);
            assertEquals(1, restartAttempts.get());
            assertEquals(0, messages(queue));
            assertEquals(1, consumers(queue));
        } finally {
            restarted.stop();
        }
    }

    @Test
    void connectionFailureIsLeftToRecoveryAndThePendingRetryIsDeliveredOnce() throws Exception {
        final String name = "lc-recovery-actor";
        final String queue = NamingUtils.queueName(PREFIX, name);
        final AtomicInteger attempts = new AtomicInteger();
        final UnmanagedBaseActor<TestMessage> actor = startActor(name, 2, 4_000, (message, metadata) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("fail; retry later");
            }
            return true;
        });
        try {
            await().atMost(10, TimeUnit.SECONDS).until(() -> consumers(queue) == 2);
            actor.publish(new TestMessage("m1"));
            await().atMost(15, TimeUnit.SECONDS).until(() -> attempts.get() == 1);

            // The broker drops the actor's connection while the retry waits in its delay queue.
            closeConnectionsNamed(CONNECTION_NAME);

            // Automatic recovery restores exactly the configured consumers; no extra channel is re-created.
            await().atMost(30, TimeUnit.SECONDS).pollDelay(4, TimeUnit.SECONDS).until(() -> consumers(queue) == 2);
            TimeUnit.SECONDS.sleep(5);
            assertEquals(2, consumers(queue));

            // The pending retry is delivered exactly once.
            await().atMost(20, TimeUnit.SECONDS).until(() -> attempts.get() == 2);
            TimeUnit.SECONDS.sleep(2);
            assertEquals(2, attempts.get());
            assertEquals(0, messages(queue));
        } finally {
            actor.stop();
        }
    }

    private void closeConnectionsNamed(final String connectionName) throws Exception {
        final String auth = "Basic " + Base64.getEncoder().encodeToString(
                (broker.getAdminUsername() + ":" + broker.getAdminPassword()).getBytes(StandardCharsets.UTF_8));
        final HttpClient http = HttpClient.newHttpClient();
        final String api = broker.getHttpUrl() + "/api";
        int closed = 0;
        for (int attempt = 0; attempt < 20 && closed == 0; attempt++) {
            // The management API lists a new connection after a short delay.
            final HttpResponse<String> list = http.send(HttpRequest.newBuilder(URI.create(api + "/connections"))
                    .header("Authorization", auth).build(), HttpResponse.BodyHandlers.ofString());
            for (final JsonNode node : mapper.readTree(list.body())) {
                final JsonNode properties = node.path("client_properties");
                if (connectionName.equals(properties.path("connection_name").asText())) {
                    final String encoded = URLEncoder.encode(node.get("name").asText(), StandardCharsets.UTF_8)
                            .replace("+", "%20");
                    http.send(HttpRequest.newBuilder(URI.create(api + "/connections/" + encoded))
                            .header("Authorization", auth).DELETE().build(), HttpResponse.BodyHandlers.discarding());
                    closed++;
                }
            }
            if (closed == 0) {
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }
        assertEquals(1, closed, "expected to force-close the actor's connection");
    }
}
