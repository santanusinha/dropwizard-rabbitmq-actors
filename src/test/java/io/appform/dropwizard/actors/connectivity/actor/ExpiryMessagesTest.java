package io.appform.dropwizard.actors.connectivity.actor;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.base.UnmanagedConsumer;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.metrics.RMQMetricObserver;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.utils.RMQContainer;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;

@Slf4j
public class ExpiryMessagesTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleTestApplication.class);
    private static final String RABBITMQ_USERNAME = "guest";
    private static final String RABBITMQ_PASSWORD = "guest";
    private static RMQConnection connection;
    private static final MetricRegistry metricRegistry = new MetricRegistry();

    @BeforeAll
    @SneakyThrows
    public static void beforeMethod() {
        System.setProperty("dw." + "server.applicationConnectors[0].port", "0");
        System.setProperty("dw." + "server.adminConnectors[0].port", "0");

        app.before();

        val config = getRMQConfig(RMQContainer.startContainer());

        connection = new RMQConnection("test-conn", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));
        connection.start();

    }

    @AfterAll
    @SneakyThrows
    public static void afterMethod() {
        app.after();
    }

    private static RMQConfig getRMQConfig(RabbitMQContainer rabbitmqContainer) {
        val rmqConfig = new RMQConfig();
        val mappedPort = rabbitmqContainer.getMappedPort(5672);
        val host = rabbitmqContainer.getContainerIpAddress();
        val brokers = new ArrayList<Broker>();
        brokers.add(new Broker(host, mappedPort));
        rmqConfig.setBrokers(brokers);
        rmqConfig.setUserName(RABBITMQ_USERNAME);
        rmqConfig.setPassword(RABBITMQ_PASSWORD);
        rmqConfig.setVirtualHost("/");
        log.info("RabbitMQ connection details: {}", rmqConfig);
        return rmqConfig;
    }

    /**
     * This test does the following:
     * - Publisher publishes the message with an expiry of 1500ms with 0 consumers
     * - After 1510ms, starting a consumer
     * - Consumer would consume the message in expired handler and delay in consumption should be more than 1500ms
     */
    @Test
    public void testWhenMessagesAreExpired() throws Exception {
        val queueName = "queue-1";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        val publisher = new UnmanagedPublisher<>(
                queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "value"
        );
        publisher.publishWithExpiry(message, 1500);

        Thread.sleep(1510);

        val expiredDeliveryCount = new AtomicInteger();
        val consumer = new UnmanagedConsumer<>(
                queueName, actorConfig, connection, objectMapper, new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                Map.class, this::handleForNoExpectedMsg, handleExpiredMessage(expiredDeliveryCount), (x) -> true);
        consumer.start();

        Thread.sleep(1000);

        Assertions.assertEquals(1, expiredDeliveryCount.getAndIncrement());
    }

    /**
     * This test does the following:
     * - Publisher publishes the message with an expiry of 1500ms with 0 consumers and with retry
     * - After 1510ms, starting a consumer
     * - Consumer would consume the message in expired handler and thrown an exception for the first attempt
     * - And would consume the message in second attempt
     * - Here the count should be 2 and delay should be more than 1500ms
     */
    @Test
    public void testReDeliveryOfExpiredMessages() throws Exception {
        val queueName = "queue-5";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        actorConfig.setRetryConfig(CountLimitedFixedWaitRetryConfig.builder()
                        .maxAttempts(2)
                        .waitTime(io.dropwizard.util.Duration.milliseconds(100))
                .build());
        val publisher = new UnmanagedPublisher<>(
                queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "value"
        );
        publisher.publishWithExpiry(message, 1500);

        Thread.sleep(1510);

        val expiredDeliveryCount = new AtomicInteger();
        val consumer = new UnmanagedConsumer<>(
                queueName, actorConfig, connection, objectMapper, new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                Map.class, this::handleForNoExpectedMsg, handleExpectedMessageForReDelivery(expiredDeliveryCount), (x) -> true);
        consumer.start();

        Thread.sleep(1000);

        Assertions.assertEquals(2, expiredDeliveryCount.getAndIncrement());
    }

    /**
     * This test does the following:
     * - Publisher publishes the message with an expiry of 1500ms with 0 consumers
     * - After 500ms, starting a consumer
     * - Consumer would consume the message normally and delay in consumption should be more than 500ms and less than 1500ms
     */
    @Test
    public void testWhenMessagesAreNotExpiredCase1() throws Exception {
        val queueName = "queue-2";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        val publisher = new UnmanagedPublisher<>(
                queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "value"
        );
        publisher.publishWithExpiry(message, 1500);

        Thread.sleep(500);

        val normalDeliveryCount = new AtomicInteger();
        val consumer = new UnmanagedConsumer<>(
                queueName, actorConfig, connection, objectMapper, new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                Map.class, handleDelayedMessageConsumption(normalDeliveryCount), this::handleForNoExpectedMsg, (x) -> true);
        consumer.start();

        Thread.sleep(500);

        Assertions.assertEquals(1, normalDeliveryCount.get());
    }

    /**
     * This test does the following:
     * - Publisher publishes the message with an expiry of 1500ms with 0 consumers
     * - Starting a consumer immediately but after publishing
     * - Consumer would consume the message normally and delay in consumption should be less than 1500ms
     */
    @Test
    public void testWhenMessagesAreNotExpiredCase2() throws Exception {
        val queueName = "queue-3";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        val publisher = new UnmanagedPublisher<>(
                queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "value"
        );
        publisher.publishWithExpiry(message, 1500);

        val normalDeliveryCount = new AtomicInteger();
        val consumer = new UnmanagedConsumer<>(
                queueName, actorConfig, connection, objectMapper, new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                Map.class, handleExpectedMessage(1500, normalDeliveryCount), this::handleForNoExpectedMsg, (x) -> true);
        consumer.start();

        Thread.sleep(500);

        Assertions.assertEquals(1, normalDeliveryCount.get());
    }

    /**
     * This test does the following:
     * - Publisher publishes the message with an expiry of 1500ms with 1 consumer
     * - Consumer would consume the message normally and delay in consumption should be less than 1500ms
     */
    @Test
    public void testWhenMessagesAreNotExpiredCase3() throws Exception {
        val queueName = "queue-4";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        val publisher = new UnmanagedPublisher<>(
                queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "value"
        );

        val normalDeliveryCount = new AtomicInteger();
        val consumer = new UnmanagedConsumer<>(
                queueName, actorConfig, connection, objectMapper, new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                Map.class, handleExpectedMessage(1000, normalDeliveryCount), this::handleForNoExpectedMsg, (x) -> true);
        consumer.start();

        publisher.publishWithExpiry(message, 1500);
        Thread.sleep(1000);

        Assertions.assertEquals(1, normalDeliveryCount.get());
    }

    /**
     * This test does the following:
     * - Publisher publishes the message with an expiry of -1ms with 1 consumer
     * - Consumer would consume the message normally and delay in consumption is 1000ms
     */
    @Test
    public void testWhenMessagesAreNotExpiredCase4() throws Exception {
        val queueName = "queue-6";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        val publisher = new UnmanagedPublisher<>(
                queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "value"
        );

        publisher.publishWithExpiry(message, -1);
        Thread.sleep(1000);
        val normalDeliveryCount = new AtomicInteger();
        val consumer = new UnmanagedConsumer<>(
                queueName, actorConfig, connection, objectMapper, new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                Map.class, handleExpectedMessageWithDelay(1000, normalDeliveryCount), this::handleForNoExpectedMsg, (x) -> true);
        consumer.start();


        Assertions.assertEquals(1, normalDeliveryCount.get());
    }

    /**
     * This test does the following:
     * - Publisher publishes the message with 1 consumer
     * - Consumer would consume the message normally and delay in consumption is 1000ms
     */
    @Test
    public void testRegressionForPublish() throws Exception {
        val queueName = "queue-7";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        val publisher = new UnmanagedPublisher<>(
                queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "value"
        );

        publisher.publish(message);
        val normalDeliveryCount = new AtomicInteger();
        val consumer = new UnmanagedConsumer<>(
                queueName, actorConfig, connection, objectMapper, new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                Map.class, handleExpectedMessageWithDelay(0, normalDeliveryCount), this::handleForNoExpectedMsg, (x) -> true);
        consumer.start();
        Thread.sleep(500);

        Assertions.assertEquals(1, normalDeliveryCount.get());
    }
    @NotNull
    private MessageHandlingFunction<Map, Boolean> handleExpectedMessage(int maxDelay, AtomicInteger normalDeliveryCount) {
        return (msg, meta) -> {
            Assertions.assertTrue(meta.getDelayInMs() < maxDelay);
            normalDeliveryCount.getAndIncrement();
            return true;
        };
    }

    @NotNull
    private MessageHandlingFunction<Map, Boolean> handleExpectedMessageWithDelay(int minDelay, AtomicInteger normalDeliveryCount) {
        return (msg, meta) -> {
            Assertions.assertTrue(meta.getDelayInMs() > minDelay);
            normalDeliveryCount.getAndIncrement();
            return true;
        };
    }

    @NotNull
    private MessageHandlingFunction<Map, Boolean> handleExpectedMessageForReDelivery(AtomicInteger expiredDeliveryCount) {
        return (msg, meta) -> {
            log.info("Meta::{}", meta);
            Assertions.assertTrue(meta.getDelayInMs() > 1500);
            expiredDeliveryCount.getAndIncrement();

            if (expiredDeliveryCount.get() > 1) {
                return true;
            } else {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    public void testPublishWithExpiryAndDelay() throws Exception {
        val queueName = "queue-1";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-delay");
        val publisher = new UnmanagedPublisher<>(
                queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "test"
        );
        publisher.publishWithDelayAndExpiry(message, 1500, 500);

        Thread.sleep(1510);


        val expiredDeliveryCount = new AtomicInteger();
        val consumer = new UnmanagedConsumer<>(
                queueName, actorConfig, connection, objectMapper, new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                Map.class, this::handleForNoExpectedMsg, handleExpiredMessageWithDelay(expiredDeliveryCount), (x) -> true);
        consumer.start();

        Thread.sleep(1000);

        Assertions.assertEquals(1, expiredDeliveryCount.getAndIncrement());
    }

    @NotNull
    private MessageHandlingFunction<Map, Boolean> handleExpiredMessage(AtomicInteger expiredDeliveryCount) {
        return (msg, meta) -> {
            log.info("Meta::{}", meta);
            Assertions.assertTrue(meta.getDelayInMs() > 1500);
            expiredDeliveryCount.getAndIncrement();
            return true;
        };
    }

    @NotNull
    private MessageHandlingFunction<Map, Boolean> handleExpiredMessageWithDelay(AtomicInteger expiredDeliveryCount) {
        return (msg, meta) -> {
            Assertions.assertTrue(meta.getDelayInMs() > 1500);
            Map<String, Object> msgHeaders = meta.getHeaders();
            Assertions.assertNotNull(msgHeaders);
            Assertions.assertTrue(StringUtils.equals("500", String.valueOf(msgHeaders.get("x-delay"))));
            expiredDeliveryCount.getAndIncrement();
            return true;
        };
    }

    @NotNull
    private MessageHandlingFunction<Map, Boolean> handleDelayedMessageConsumption(AtomicInteger normalDeliveryCount) {
        return (msg, meta) -> {
            Assertions.assertTrue(meta.getDelayInMs() > 500 && meta.getDelayInMs() < 1500);
            normalDeliveryCount.getAndIncrement();
            return true;
        };
    }

    private <Message> boolean handleForNoExpectedMsg(Message msg, MessageMetadata messageMetadata) {
        Assertions.fail();
        return true;
    }
}
