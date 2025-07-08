package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.HaMode;
import io.appform.dropwizard.actors.actor.QueueType;
import io.appform.dropwizard.actors.base.UnmanagedConsumer;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.junit.extension.RabbitMQExtension;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.utils.RMQTestUtils;
import io.dropwizard.testing.junit5.DropwizardAppExtension;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.RabbitMQContainer;

@Slf4j
@ExtendWith(RabbitMQExtension.class)
public class QueueTypesTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app = new DropwizardAppExtension<>(
            RabbitMQBundleTestApplication.class);
    public static final String TEST_EXCHANGE = "test-exchange";
    private static RMQConnection connection;

    @BeforeAll
    @SneakyThrows
    public static void beforeMethod() {
        app.before();
    }

    @BeforeEach
    void setUp(final RabbitMQContainer rabbitMQContainer) throws Exception {
        RMQConfig config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        connection = new RMQConnection("test-conn", config, Executors.newSingleThreadExecutor(), app.getEnvironment(),
                TtlConfig.builder()
                        .build(), new TerminalRMQObserver());
        connection.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.stop();
    }

    @AfterAll
    @SneakyThrows
    public static void afterMethod() {
        app.after();
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
        UnmanagedPublisher<Object> publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection,
                objectMapper);
        publisher.start();

        ImmutableMap<String, String> message = ImmutableMap.of("key", "test-message");
        publisher.publish(message);

        UnmanagedConsumer<Map> consumer = new UnmanagedConsumer<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig.getPrefetchCount(), actorConfig.getConcurrency(),actorConfig.isSharded(), actorConfig.getShardCount() , actorConfig.getConsumer(), connection,
                objectMapper, new RetryStrategyFactory().create(actorConfig.getRetryConfig()), new ExceptionHandlingFactory().create(actorConfig.getExceptionHandlerConfig()), Map.class, (msg, metadata) -> {
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
