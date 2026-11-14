package io.appform.dropwizard.actors.messaging;

import static io.dropwizard.util.Duration.milliseconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.config.RmqManagementConfig;
import io.appform.dropwizard.actors.messaging.config.ExchangeSpec;
import io.appform.dropwizard.actors.messaging.config.MessagingConfig;
import io.appform.dropwizard.actors.messaging.config.QueueSpec;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.dropwizard.setup.Environment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

/**
 * Messaging against a real RabbitMQ broker with the delayed-message and management plugins: routing, sideline and
 * retry isolation, unroutable detection, binding drift checks and the consumer lifecycle.
 */
class MessagingIntegrationTest {

    private static final String RABBITMQ_IMAGE = "rabbitmq:3.12.12-management";
    private static final String DELAYED_PLUGIN_URL = "https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/"
            + "releases/download/v3.12.0/rabbitmq_delayed_message_exchange-3.12.0.ez";
    private static final Duration WAIT = Duration.ofSeconds(15);
    /**
     * How long to wait before concluding that a message was not delivered.
     */
    private static final long QUIET_PERIOD_MS = 1_000;

    private static RabbitMQContainer rabbitMQ;
    private static Connection adminConnection;

    private final List<Messaging> started = new ArrayList<>();
    private final List<ConnectionRegistry> registries = new ArrayList<>();
    private final Map<String, List<String>> received = new ConcurrentHashMap<>();
    private String prefix;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestMessage {
        private String id;
        private String key;
    }

    /**
     * Optional prebuilt broker image (e.g. one built by an earlier run), for environments where the image build can't
     * download the plugin. Unset: the image is built from {@link #RABBITMQ_IMAGE} plus the plugin.
     */
    private static final String PREBUILT_IMAGE_PROPERTY = "messaging.it.image";

    @BeforeAll
    static void startBroker() throws Exception {
        final String prebuilt = System.getProperty(PREBUILT_IMAGE_PROPERTY);
        final String image = prebuilt != null && !prebuilt.isBlank() ? prebuilt : buildImage();
        rabbitMQ = new RabbitMQContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("rabbitmq"));
        rabbitMQ.start();
        final ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMQ.getHost());
        factory.setPort(rabbitMQ.getAmqpPort());
        factory.setUsername(rabbitMQ.getAdminUsername());
        factory.setPassword(rabbitMQ.getAdminPassword());
        adminConnection = factory.newConnection();
    }

    private static String buildImage() {
        return new ImageFromDockerfile("dropwizard-rabbitmq-actors-messaging-it", false)
                .withDockerfileFromBuilder(builder -> builder
                        .from(RABBITMQ_IMAGE)
                        .add(DELAYED_PLUGIN_URL, "/opt/rabbitmq/plugins/")
                        .run("chmod 644 /opt/rabbitmq/plugins/rabbitmq_delayed_message_exchange-3.12.0.ez "
                                + "&& rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange")
                        .build())
                .get();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (adminConnection != null) {
            adminConnection.close();
        }
        if (rabbitMQ != null) {
            rabbitMQ.stop();
        }
    }

    private static int testCounter = 0;

    @BeforeEach
    void uniquePrefix() {
        prefix = "it" + (++testCounter) + ".messaging";
    }

    @AfterEach
    void stopEverything() throws Exception {
        for (final Messaging messaging : started) {
            messaging.stop();
        }
        registries.forEach(ConnectionRegistry::stop);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static RMQConfig rmqConfig() {
        final RMQConfig config = new RMQConfig();
        final List<Broker> brokers = new ArrayList<>();
        brokers.add(new Broker(rabbitMQ.getHost(), rabbitMQ.getAmqpPort()));
        config.setBrokers(brokers);
        config.setUserName(rabbitMQ.getAdminUsername());
        config.setPassword(rabbitMQ.getAdminPassword());
        config.setVirtualHost("/");
        config.setThreadPoolSize(8);
        config.setManagementConfig(RmqManagementConfig.builder()
                .host(rabbitMQ.getHost())
                .port(rabbitMQ.getHttpPort())
                .build());
        return config;
    }

    private Messaging messaging(final Map<String, ExchangeSpec> exchanges) {
        final RMQConfig rmqConfig = rmqConfig();
        final Environment environment = mock(Environment.class);
        when(environment.metrics()).thenReturn(new MetricRegistry());
        when(environment.healthChecks()).thenReturn(new HealthCheckRegistry());
        final ConnectionRegistry registry = new ConnectionRegistry(environment,
                (name, size) -> Executors.newFixedThreadPool(size), rmqConfig, TtlConfig.builder().build(),
                new TerminalRMQObserver());
        registries.add(registry);
        return new Messaging(MessagingConfig.builder().prefix(prefix).exchanges(new LinkedHashMap<>(exchanges)).build(),
                rmqConfig, registry, new ObjectMapper());
    }

    private Messaging start(final Messaging messaging) throws Exception {
        messaging.start();
        started.add(messaging);
        messaging.startConsumers();
        return messaging;
    }

    private static ExchangeSpec exchange(final ExchangeType type, final Map<String, QueueSpec> queues) {
        return ExchangeSpec.builder().type(type).queues(new LinkedHashMap<>(queues)).build();
    }

    private static QueueSpec queue(final String... keys) {
        return QueueSpec.builder().bindingKeys(List.of(keys)).concurrency(1).build();
    }

    private QueueHandler<TestMessage> recording(final String queue) {
        return (message, context) -> {
            received.computeIfAbsent(queue, q -> new CopyOnWriteArrayList<>()).add(message.getId());
            return true;
        };
    }

    private void consumeAll(final Messaging messaging, final String exchange, final String... queues) {
        for (final String queue : queues) {
            messaging.consumer(exchange, queue, TestMessage.class, recording(queue));
        }
    }

    private List<String> receivedBy(final String queue) {
        return received.getOrDefault(queue, List.of());
    }

    private String brokerQueue(final String queue) {
        return MessagingNames.queue(prefix, queue);
    }

    private static long messageCount(final String brokerQueue) throws Exception {
        try (Channel channel = adminConnection.createChannel()) {
            return channel.queueDeclarePassive(brokerQueue).getMessageCount();
        }
    }

    /**
     * Waits {@link #QUIET_PERIOD_MS}, so that a delivery that should not happen has the time to happen.
     */
    private static void quietPeriod() {
        await().pollDelay(Duration.ofMillis(QUIET_PERIOD_MS)).atMost(Duration.ofMillis(QUIET_PERIOD_MS * 3))
                .until(() -> true);
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void routesByExchangeType() throws Exception {
        final Map<String, ExchangeSpec> exchanges = new LinkedHashMap<>();
        exchanges.put("it_direct", exchange(ExchangeType.DIRECT, Map.of("D1", queue("a"), "D2", queue("a", "b"))));
        exchanges.put("it_topic", exchange(ExchangeType.TOPIC,
                Map.of("T_ALL", queue("order.#"), "T_PLACED", queue("order.*.PLACED"))));
        exchanges.put("it_fanout", exchange(ExchangeType.FANOUT,
                Map.of("F1", QueueSpec.builder().concurrency(1).build(),
                        "F2", QueueSpec.builder().concurrency(1).build())));
        final Messaging messaging = messaging(exchanges);
        final MessagePublisher<TestMessage> direct = messaging.publisher("it_direct", TestMessage::getKey);
        final MessagePublisher<TestMessage> topic = messaging.publisher("it_topic", TestMessage::getKey);
        final MessagePublisher<TestMessage> fanout = messaging.publisher("it_fanout");
        consumeAll(messaging, "it_direct", "D1", "D2");
        consumeAll(messaging, "it_topic", "T_ALL", "T_PLACED");
        consumeAll(messaging, "it_fanout", "F1", "F2");
        start(messaging);

        direct.publish(new TestMessage("direct-a", "a"));
        direct.publish(new TestMessage("direct-b", "b"));
        topic.publish(new TestMessage("placed", "order.IN.PLACED"));
        topic.publish(new TestMessage("cancelled", "order.IN.CANCELLED"));
        topic.publish(new TestMessage("deep", "order.IN.x.PLACED"));
        fanout.publish(new TestMessage("fan", null));

        await().atMost(WAIT).until(() -> receivedBy("D2").size() == 2 && receivedBy("T_ALL").size() == 3
                && receivedBy("F1").size() == 1 && receivedBy("F2").size() == 1);
        quietPeriod();
        assertEquals(List.of("direct-a"), receivedBy("D1"));
        assertEquals(List.of("direct-a", "direct-b"), receivedBy("D2"));
        assertEquals(List.of("placed", "cancelled", "deep"), receivedBy("T_ALL"));
        assertEquals(List.of("placed"), receivedBy("T_PLACED"));  // '*' matches exactly one word
        assertEquals(List.of("fan"), receivedBy("F1"));
        assertEquals(List.of("fan"), receivedBy("F2"));
    }

    @Test
    void rejectedMessageReachesOnlyItsOwnSideline() throws Exception {
        final Messaging messaging = messaging(Map.of("it_fanout", exchange(ExchangeType.FANOUT,
                Map.of("REJECTS", QueueSpec.builder().concurrency(1).build(),
                        "ACCEPTS", QueueSpec.builder().concurrency(1).build()))));
        final MessagePublisher<TestMessage> fanout = messaging.publisher("it_fanout");
        messaging.consumer("it_fanout", "REJECTS", TestMessage.class, (message, context) -> false);
        messaging.consumer("it_fanout", "ACCEPTS", TestMessage.class, recording("ACCEPTS"));
        start(messaging);

        fanout.publish(new TestMessage("m1", null));

        await().atMost(WAIT).until(() -> messageCount(MessagingNames.sidelineQueue(brokerQueue("REJECTS"))) == 1);
        quietPeriod();
        assertEquals(List.of("m1"), receivedBy("ACCEPTS"));
        assertEquals(0, messageCount(MessagingNames.sidelineQueue(brokerQueue("ACCEPTS"))));
        assertEquals(0, messageCount(brokerQueue("REJECTS")));
    }

    @Test
    void retryLaterRedeliversOnlyToTheCallingQueue() throws Exception {
        final Messaging messaging = messaging(Map.of("it_fanout", exchange(ExchangeType.FANOUT,
                Map.of("RETRIES", QueueSpec.builder().concurrency(1).retryLaterEnabled(true).build(),
                        "OTHER", QueueSpec.builder().concurrency(1).build()))));
        final MessagePublisher<TestMessage> fanout = messaging.publisher("it_fanout");
        messaging.consumer("it_fanout", "RETRIES", TestMessage.class, (message, context) -> {
            final List<String> deliveries = received.computeIfAbsent("RETRIES", q -> new CopyOnWriteArrayList<>());
            deliveries.add(message.getId());
            if (deliveries.size() == 1) {
                context.retryLater(message, Duration.ofMillis(300));
            }
            return true;
        });
        messaging.consumer("it_fanout", "OTHER", TestMessage.class, recording("OTHER"));
        start(messaging);

        fanout.publish(new TestMessage("m1", null));

        await().atMost(WAIT).until(() -> receivedBy("RETRIES").size() == 2);
        quietPeriod();
        assertEquals(List.of("m1", "m1"), receivedBy("RETRIES"));
        assertEquals(List.of("m1"), receivedBy("OTHER"));
    }

    @Test
    void unmatchedRoutingKeyFailsThePublishWithConfirms() throws Exception {
        final Messaging messaging = messaging(Map.of("it_direct", ExchangeSpec.builder()
                .type(ExchangeType.DIRECT)
                .publisherConfirms(true)
                .queues(new LinkedHashMap<>(Map.of("D1", queue("a"))))
                .build()));
        final MessagePublisher<TestMessage> direct = messaging.publisher("it_direct", TestMessage::getKey);
        consumeAll(messaging, "it_direct", "D1");
        start(messaging);

        final TestMessage unroutable = new TestMessage("m1", "nobody");
        final UnroutableMessageException e = assertThrows(UnroutableMessageException.class,
                () -> direct.publish(unroutable));
        assertTrue(e.getMessage().contains("nobody"), e.getMessage());
        direct.publish(new TestMessage("m2", "a"));  // the publisher keeps working
        await().atMost(WAIT).until(() -> receivedBy("D1").size() == 1);
    }

    @Test
    void unmatchedRoutingKeyIsOnlyLoggedWithoutConfirms() throws Exception {
        final Messaging messaging = messaging(Map.of("it_direct", ExchangeSpec.builder()
                .type(ExchangeType.DIRECT)
                .publisherConfirms(false)
                .queues(new LinkedHashMap<>(Map.of("D1", queue("a"))))
                .build()));
        final MessagePublisher<TestMessage> direct = messaging.publisher("it_direct", TestMessage::getKey);
        consumeAll(messaging, "it_direct", "D1");
        start(messaging);

        direct.publish(new TestMessage("m1", "nobody"));  // returned by the broker, but the publish does not fail
        direct.publish(new TestMessage("m2", "a"));
        await().atMost(WAIT).until(() -> receivedBy("D1").size() == 1);
        quietPeriod();
        assertEquals(List.of("m2"), receivedBy("D1"));
    }

    @Test
    void driftCheckRejectsChangedBindingKeys() throws Exception {
        final Messaging first = messaging(Map.of("it_direct",
                exchange(ExchangeType.DIRECT, Map.of("D1", queue("a")))));
        consumeAll(first, "it_direct", "D1");
        start(first).stop();

        final Messaging changed = messaging(Map.of("it_direct",
                exchange(ExchangeType.DIRECT, Map.of("D1", queue("a", "c")))));
        consumeAll(changed, "it_direct", "D1");
        final IllegalStateException e = assertThrows(IllegalStateException.class, changed::start);
        assertTrue(e.getMessage().contains("immutable"), e.getMessage());
    }

    @Test
    void turningRetryLaterOffKeepsStartupWorking() throws Exception {
        final Messaging withRetry = messaging(Map.of("it_fanout", exchange(ExchangeType.FANOUT,
                Map.of("F1", QueueSpec.builder().concurrency(1).retryLaterEnabled(true).build()))));
        consumeAll(withRetry, "it_fanout", "F1");
        start(withRetry).stop();

        final Messaging withoutRetry = messaging(Map.of("it_fanout", exchange(ExchangeType.FANOUT,
                Map.of("F1", QueueSpec.builder().concurrency(1).retryLaterEnabled(false).build()))));
        final MessagePublisher<TestMessage> fanout = withoutRetry.publisher("it_fanout");
        consumeAll(withoutRetry, "it_fanout", "F1");
        start(withoutRetry);

        fanout.publish(new TestMessage("m1", null));
        await().atMost(WAIT).until(() -> receivedBy("F1").size() == 1);
    }

    @Test
    void noHandlerRunsAfterStopConsumers() throws Exception {
        final Messaging messaging = messaging(Map.of("it_fanout", exchange(ExchangeType.FANOUT,
                Map.of("F1", QueueSpec.builder().concurrency(2).build()))));
        final MessagePublisher<TestMessage> fanout = messaging.publisher("it_fanout");
        consumeAll(messaging, "it_fanout", "F1");
        start(messaging);
        fanout.publish(new TestMessage("before", null));
        await().atMost(WAIT).until(() -> receivedBy("F1").size() == 1);

        messaging.stopConsumers();
        quietPeriod();  // a re-subscribe after the consumer channels close would happen here
        fanout.publish(new TestMessage("after", null));  // publishers keep working
        quietPeriod();

        assertEquals(List.of("before"), receivedBy("F1"));
        assertEquals(1, messageCount(brokerQueue("F1")));
        try (Channel channel = adminConnection.createChannel()) {
            assertEquals(0, channel.consumerCount(brokerQueue("F1")));
        }
    }

    // --- SYNC retries through the (async-capable) retry machinery ---------------------------------------------------

    private static CountLimitedFixedWaitRetryConfig syncRetry(
            final int maxAttempts) {
        final CountLimitedFixedWaitRetryConfig retry =
                new CountLimitedFixedWaitRetryConfig();
        retry.setMaxAttempts(maxAttempts);
        retry.setWaitTime(milliseconds(100));
        return retry;
    }

    private Messaging retryingFanout(final QueueHandler<TestMessage> handler) {
        final Messaging messaging = messaging(Map.of("it_fanout", exchange(ExchangeType.FANOUT,
                Map.of("RETRIES", QueueSpec.builder().concurrency(1).retryConfig(syncRetry(3)).build(),
                        "OTHER", QueueSpec.builder().concurrency(1).build()))));
        messaging.consumer("it_fanout", "RETRIES", TestMessage.class, handler);
        messaging.consumer("it_fanout", "OTHER", TestMessage.class, recording("OTHER"));
        return messaging;
    }

    @Test
    void syncRetryRecoversWithinTheAttemptBudget() throws Exception {
        final AtomicInteger attempts = new AtomicInteger();
        final Messaging messaging = retryingFanout((message, context) -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient failure " + attempts.get());
            }
            return true;
        });
        final MessagePublisher<TestMessage> fanout = messaging.publisher("it_fanout");
        start(messaging);

        fanout.publish(new TestMessage("m1", null));

        await().atMost(WAIT).until(() -> attempts.get() == 3);
        quietPeriod();
        assertEquals(3, attempts.get());   // retried in-process, then acked
        assertEquals(0, messageCount(brokerQueue("RETRIES")));
        assertEquals(0, messageCount(MessagingNames.sidelineQueue(brokerQueue("RETRIES"))));
        assertEquals(List.of("m1"), receivedBy("OTHER"));   // retries never reach the other queue
    }

    @Test
    void syncRetryExhaustionSidelinesToTheQueuesOwnSideline() throws Exception {
        final AtomicInteger attempts = new AtomicInteger();
        final Messaging messaging = retryingFanout((message, context) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("permanent failure");
        });
        final MessagePublisher<TestMessage> fanout = messaging.publisher("it_fanout");
        start(messaging);

        fanout.publish(new TestMessage("m1", null));

        await().atMost(WAIT).until(() -> messageCount(MessagingNames.sidelineQueue(brokerQueue("RETRIES"))) == 1);
        quietPeriod();
        assertEquals(3, attempts.get());
        assertEquals(0, messageCount(MessagingNames.sidelineQueue(brokerQueue("OTHER"))));
        assertEquals(List.of("m1"), receivedBy("OTHER"));
    }

    @Test
    void droppedExceptionIsAckedInsteadOfSidelinedOnceRetriesAreExhausted() throws Exception {
        final AtomicInteger attempts = new AtomicInteger();
        final Messaging messaging = retryingFanout(new QueueHandler<>() {
            @Override
            public boolean handle(final TestMessage message, final MessageContext<TestMessage> context) {
                attempts.incrementAndGet();
                throw new UnsupportedOperationException("not for us");
            }

            @Override
            public Set<Class<? extends Throwable>> droppedExceptionTypes() {
                return Set.of(UnsupportedOperationException.class);
            }
        });
        final MessagePublisher<TestMessage> fanout = messaging.publisher("it_fanout");
        start(messaging);

        fanout.publish(new TestMessage("m1", null));

        await().atMost(WAIT).until(() -> attempts.get() == 3);
        quietPeriod();
        assertEquals(3, attempts.get());
        assertEquals(0, messageCount(brokerQueue("RETRIES")));
        assertEquals(0, messageCount(MessagingNames.sidelineQueue(brokerQueue("RETRIES"))));
    }
}

