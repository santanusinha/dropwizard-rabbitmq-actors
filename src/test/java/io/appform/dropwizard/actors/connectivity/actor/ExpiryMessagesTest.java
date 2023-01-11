package io.appform.dropwizard.actors.connectivity.actor;

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
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.testcontainers.rabbitmq.RabbitMQStatusCheck;
import io.appform.testcontainers.rabbitmq.config.RabbitMQContainerConfiguration;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ExpiryMessagesTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleTestApplication.class);
    private static final int RABBITMQ_MANAGEMENT_PORT = 15672;
    private static final String RABBITMQ_DOCKER_IMAGE = "rabbitmq:3.8.34-management";
    private static final String RABBITMQ_USERNAME = "guest";
    private static final String RABBITMQ_PASSWORD = "guest";
    private static RMQConnection connection;
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @BeforeClass
    @SneakyThrows
    public static void beforeMethod() {
        app.before();

        val rabbitMQContainer = rabbitMQContainer();
        val config = getRMQConfig(rabbitMQContainer);

        connection = new RMQConnection("test-conn", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build());
        connection.start();

    }

    @AfterClass
    @SneakyThrows
    public static void afterMethod() {
        app.after();
    }

    private static GenericContainer rabbitMQContainer() {
        val containerConfiguration = new RabbitMQContainerConfiguration();
        log.info("Starting rabbitMQ server. Docker image: {}", containerConfiguration.getDockerImage());

        GenericContainer rabbitMQ =
                new GenericContainer(RABBITMQ_DOCKER_IMAGE)
                        .withEnv("RABBITMQ_DEFAULT_VHOST", containerConfiguration.getVhost())
                        .withEnv("RABBITMQ_DEFAULT_USER", RABBITMQ_USERNAME)
                        .withEnv("RABBITMQ_DEFAULT_PASS", RABBITMQ_PASSWORD)
                        .withExposedPorts(containerConfiguration.getPort(), RABBITMQ_MANAGEMENT_PORT)
                        .waitingFor(new RabbitMQStatusCheck(containerConfiguration))
                        .withStartupTimeout(Duration.ofSeconds(45));

        rabbitMQ = rabbitMQ.withStartupCheckStrategy(new IsRunningStartupCheckStrategyWithDelay());
        rabbitMQ.start();
        log.info("Started RabbitMQ server");
        return rabbitMQ;
    }

    private static RMQConfig getRMQConfig(GenericContainer rabbitmqContainer) {
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

    @NotNull
    private MessageHandlingFunction<Map, Boolean> handleExpectedMessage(int maxDelay, AtomicInteger normalDeliveryCount) {
        return (msg, meta) -> {
            Assertions.assertTrue(meta.getDelayInMs() < maxDelay);
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
