package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.HaMode;
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
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.RabbitMQContainer;

@Slf4j
public class QueueTypesTest {

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

    public static Stream<Arguments> provideActorConfig() {
        return Stream.of(Arguments.of(buildClassicQueue(), "classic-queue-ha-all"),
                Arguments.of(buildCustomClassicQueue(), "classic-queue-ha-2"),
                Arguments.of(buildQuorumQueue(), "quorumQueue"),
                Arguments.of(buildCustomQuorumQueue(), "quorumQueue-groupSize-5"),
                Arguments.of(buildLazyQueue(), "lazy-queue"), Arguments.of(buildClassicQueueV2(), "classic-v2"));
    }

    private static ActorConfig buildLazyQueue() {
        return ActorConfig.builder()
                .queueType(QueueType.CLASSIC)
                .lazyMode(true)
                .build();
    }

    private static ActorConfig buildCustomQuorumQueue() {
        return ActorConfig.builder()
                .queueType(QueueType.QUORUM)
                .quorumInitialGroupSize(5)
                .build();
    }

    private static ActorConfig buildQuorumQueue() {
        return ActorConfig.builder()
                .queueType(QueueType.QUORUM)
                .build();
    }

    private static ActorConfig buildCustomClassicQueue() {
        return ActorConfig.builder()
                .queueType(QueueType.CLASSIC)
                .exchange(TEST_EXCHANGE)
                .haMode(HaMode.EXACTLY)
                .haParams("2")
                .build();
    }

    private static ActorConfig buildClassicQueue() {
        return ActorConfig.builder()
                .queueType(QueueType.CLASSIC)
                .exchange(TEST_EXCHANGE)
                .build();
    }

    private static ActorConfig buildClassicQueueV2() {
        return ActorConfig.builder()
                .queueType(QueueType.CLASSIC_V2)
                .exchange(TEST_EXCHANGE)
                .build();
    }

    /**
     * This test does the following:
     * - Publisher publishes a message
     * - Consumer will consume the message and verifies that the queues are properly setup
     */
    @ParameterizedTest
    @MethodSource("provideActorConfig")
    void testConsumer(ActorConfig actorConfig,
                             String queueName) throws Exception {
        AtomicReference<Map<String, Object>> testDataHolder = new AtomicReference<>(null);
        ObjectMapper objectMapper = new ObjectMapper();
        actorConfig.setExchange("test-exchange-1");
        UnmanagedPublisher<Object> publisher = new UnmanagedPublisher<>(queueName, actorConfig, connection,
                objectMapper);
        publisher.start();

        ImmutableMap<String, String> message = ImmutableMap.of("key", "test-message");
        publisher.publish(message);

        UnmanagedConsumer<Map> consumer = new UnmanagedConsumer<>(queueName, actorConfig, connection, objectMapper,
                new RetryStrategyFactory(), new ExceptionHandlingFactory(), Map.class, (msg, metadata) -> {
            testDataHolder.set(Map.of("MESSAGE", msg));
            return true;
        }, (x, y) -> true, (x) -> true);
        consumer.start();

        Thread.sleep(1000);

        Assertions.assertNotNull(testDataHolder.get());
        Object objMsg = testDataHolder.get()
                .get("MESSAGE");
        Assertions.assertNotNull(objMsg);
        Assertions.assertInstanceOf(Map.class, objMsg);
        Map<String, String> receivedMessage = (Map<String, String>) objMsg;
        Assertions.assertEquals(message.get("key"), receivedMessage.get("key"));
    }
}
