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
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.testcontainers.rabbitmq.RabbitMQStatusCheck;
import io.appform.testcontainers.rabbitmq.config.RabbitMQContainerConfiguration;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class QueueTypesTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app = new DropwizardAppExtension<>(
            RabbitMQBundleTestApplication.class);
    public static final String TEST_EXCHANGE = "test-exchange";
    private static final int RABBITMQ_MANAGEMENT_PORT = 15672;
    private static final String RABBITMQ_DOCKER_IMAGE = "rabbitmq:3.8.34-management";
    private static final String RABBITMQ_USERNAME = "guest";
    private static final String RABBITMQ_PASSWORD = "guest";
    private static RMQConnection connection;

    @BeforeAll
    @SneakyThrows
    public static void beforeMethod() {
        System.setProperty("dw." + "server.applicationConnectors[0].port", "0");
        System.setProperty("dw." + "server.adminConnectors[0].port", "0");

        app.before();

        val rabbitMQContainer = rabbitMQContainer();
        val config = getRMQConfig(rabbitMQContainer);

        connection = new RMQConnection("test-conn", config, Executors.newSingleThreadExecutor(), app.getEnvironment(),
                TtlConfig.builder()
                        .build());
        connection.start();

    }

    @AfterAll
    @SneakyThrows
    public static void afterMethod() {
        app.after();
    }

    private static GenericContainer rabbitMQContainer() {
        val containerConfiguration = new RabbitMQContainerConfiguration();
        log.info("Starting rabbitMQ server. Docker image: {}", containerConfiguration.getDockerImage());

        GenericContainer rabbitMQ = new GenericContainer(RABBITMQ_DOCKER_IMAGE).withEnv("RABBITMQ_DEFAULT_VHOST",
                        containerConfiguration.getVhost())
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

    public static Stream<Arguments> provideActorConfig() {
        return Stream.of(Arguments.of(ActorConfig.builder()
                .queueType(QueueType.CLASSIC)
                .exchange(TEST_EXCHANGE)
                .build(), "classic-queue-ha-all"), Arguments.of(ActorConfig.builder()
                .queueType(QueueType.CLASSIC)
                .exchange(TEST_EXCHANGE)
                .haMode(HaMode.EXACTLY)
                .haParams("2")
                .build(), "classic-queue-ha-2"), Arguments.of(ActorConfig.builder()
                .queueType(QueueType.QUORUM)
                .build(), "quorumQueue"), Arguments.of(ActorConfig.builder()
                .queueType(QueueType.QUORUM)
                .quorumInitialGroupSize(5)
                .build(), "quorumQueue-groupSize-5"), Arguments.of(ActorConfig.builder()
                .queueType(QueueType.CLASSIC)
                .lazyMode(true)
                .build(), "lazy-queue"));
    }

    /**
     * This test does the following:
     * - Publisher publishes a message
     * - Consumer will consume the message and verifies that headers are accessible
     */
    @ParameterizedTest
    @MethodSource("provideActorConfig")
    public void testConsumer(ActorConfig actorConfig,
                             String queueName) throws Exception {
        AtomicReference<Map<String, Object>> testDataHolder = new AtomicReference<>(null);
        val objectMapper = new ObjectMapper();
        actorConfig.setExchange("test-exchange-1");
        val publisher = new UnmanagedPublisher<>(queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of("key", "test-message");
        publisher.publish(message);

        val consumer = new UnmanagedConsumer<>(queueName, actorConfig, connection, objectMapper,
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
