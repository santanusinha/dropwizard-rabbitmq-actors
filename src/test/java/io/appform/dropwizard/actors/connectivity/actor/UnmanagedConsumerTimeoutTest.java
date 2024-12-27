package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.QueueType;
import io.appform.dropwizard.actors.base.UnmanagedConsumer;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.utils.RMQContainer;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;

@Slf4j
public class UnmanagedConsumerTimeoutTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app = new DropwizardAppExtension<>(
            RabbitMQBundleTestApplication.class);
    public static final String TEST_EXCHANGE = "test-exchange";
    private static final String RABBITMQ_USERNAME = "guest";
    private static final String RABBITMQ_PASSWORD = "guest";
    private static RMQConnection connection;

    @BeforeAll
    @SneakyThrows
    public static void beforeMethod() {
        app.before();

        RabbitMQContainer rabbitMQContainer = RMQContainer.startContainer();
        RMQConfig config = getRMQConfig(rabbitMQContainer);

        connection = new RMQConnection("test-conn", config, Executors.newSingleThreadExecutor(), app.getEnvironment(),
                TtlConfig.builder()
                        .build(), new TerminalRMQObserver());
        connection.start();
    }

    @AfterAll
    @SneakyThrows
    public static void afterMethod() {
        app.after();
    }

    private static RMQConfig getRMQConfig(RabbitMQContainer rabbitmqContainer) {
        RMQConfig rmqConfig = new RMQConfig();
        Integer mappedPort = rabbitmqContainer.getMappedPort(5672);
        String host = rabbitmqContainer.getContainerIpAddress();
        ArrayList<Broker> brokers = new ArrayList<Broker>();
        brokers.add(new Broker(host, mappedPort));
        rmqConfig.setBrokers(brokers);
        rmqConfig.setUserName(RABBITMQ_USERNAME);
        rmqConfig.setPassword(RABBITMQ_PASSWORD);
        rmqConfig.setVirtualHost("/");
        log.info("RabbitMQ connection details: {}", rmqConfig);
        return rmqConfig;
    }

    @Test
    public void testConsumer() throws Exception {
        AtomicReference<Map<Integer, MessageMetadata>> testDataHolder = new AtomicReference<>(new HashMap<>());
        ObjectMapper objectMapper = new ObjectMapper();
        ActorConfig actorConfig = ActorConfig.builder()
                .queueType(QueueType.QUORUM)
                .exchange(TEST_EXCHANGE)
                .concurrency(1)
                .build();
        String queueName = "test-queue-1";
        UnmanagedPublisher<Object> publisher = new UnmanagedPublisher<>(queueName, actorConfig, connection,
                objectMapper);
        publisher.start();

        ImmutableMap<String, String> message = ImmutableMap.of("key", "test-message");
        publisher.publish(message);

        AtomicInteger seen = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(2);
        Thread t = new Thread(() -> {
            UnmanagedConsumer<Map> consumer = new UnmanagedConsumer<>(queueName, actorConfig, connection, objectMapper,
                    new RetryStrategyFactory(), new ExceptionHandlingFactory(), Map.class, (msg, metadata) -> {
                testDataHolder.get()
                        .put(seen.incrementAndGet(), metadata);
                if (seen.get() == 1) { // only sleep for the first time to induce consumer timeout
                    // Thread.sleep() is set to a value more than 2 times the consumer_timeout to avoid test flakiness
                    // as the RMQ periodically checks for consumer timeout
                    Thread.sleep((long) (RMQContainer.CONSUMER_TIMEOUT * 2.2));
                }
                latch.countDown();
                log.info("Processed message {}", msg);
                return true;
            }, (x, y) -> true, (x) -> true);
            try {
                consumer.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        latch.await();
        Assertions.assertNotNull(testDataHolder.get());
        log.info("Contents {}", testDataHolder.get());
        Assertions.assertEquals(2, testDataHolder.get()
                .size());
        Assertions.assertFalse(testDataHolder.get()
                .get(1)
                .isRedelivered());
        Assertions.assertTrue(testDataHolder.get()
                .get(2)
                .isRedelivered());
    }

}
