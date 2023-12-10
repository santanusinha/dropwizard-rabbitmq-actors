package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.BasicProperties.Builder;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;
import io.appform.dropwizard.actors.actor.metadata.generators.MessageMetadataGenerator;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class MessageHeadersAccessibilityTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleTestApplication.class);
    private static final String CUSTOM_HEADER_KEY = "customHeaderKey";
    private static final String CUSTOM_HEADER_VALUE = "value1234";
    private static final int RABBITMQ_MANAGEMENT_PORT = 15672;
    private static final String RABBITMQ_DOCKER_IMAGE = "rabbitmq:3.8.34-management";
    private static final String RABBITMQ_USERNAME = "guest";
    private static final String RABBITMQ_PASSWORD = "guest";
    private static RMQConnection connection;

    @BeforeClass
    @SneakyThrows
    public static void beforeMethod() {
        System.setProperty("dw." + "server.applicationConnectors[0].port", "0");
        System.setProperty("dw." + "server.adminConnectors[0].port", "0");

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

    /**
     * This test does the following:
     * - Publisher publishes a message
     * - Consumer will consume the message and verifies that headers are accessible
     */
    @Test
    public void shouldBeAbleToAccessHeadersViaMessageMetadata() throws Exception {
        AtomicReference<Map<String, Object>> testDataHolder = new AtomicReference<>(null);
        val queueName = "test-queue-1";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        actorConfig.setMessageMetaGeneratorClasses(List.of(CustomMessageMetaHeaderGenerator.class.getName()));
        val publisher = new UnmanagedPublisher<>(
                queueName, actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "test-message"
        );
        BasicProperties msgProperties = new Builder().headers(Map.of("test-header", "test-value")).build();
        publisher.publish(message, msgProperties);

        val consumer = new UnmanagedConsumer<>(
                queueName, actorConfig, connection, objectMapper, new RetryStrategyFactory(),
                new ExceptionHandlingFactory(),
                Map.class,
                (msg, metadata) -> {
                    testDataHolder.set(Map.of("MESSAGE", msg, "METADATA", metadata));
                    return true;
                },
                (x, y) -> true, (x) -> true);
        consumer.start();

        Thread.sleep(1000);

        Assertions.assertNotNull(testDataHolder.get());
        Object objMsg = testDataHolder.get().get("MESSAGE");
        Assertions.assertNotNull(objMsg);
        Assertions.assertTrue(objMsg instanceof Map);
        Map<String, String> receivedMessage = (Map<String, String>) objMsg;
        Assertions.assertEquals(message.get("key"), receivedMessage.get("key"));

        Object objMeta = testDataHolder.get().get("METADATA");
        Assertions.assertNotNull(objMeta);
        Assertions.assertTrue(objMeta instanceof MessageMetadata);
        val messageMetadata = (MessageMetadata) objMeta;

        Map<String, Object> msgHeaders = messageMetadata.getHeaders();
        Assertions.assertNotNull(msgHeaders);
        Assertions.assertTrue(StringUtils.equals("test-value", String.valueOf(msgHeaders.get("test-header"))));
        Assertions.assertTrue(StringUtils.equals(CUSTOM_HEADER_VALUE, String.valueOf(msgHeaders.get(CUSTOM_HEADER_KEY))));
    }

    public static class CustomMessageMetaHeaderGenerator implements MessageMetadataGenerator {

        @Override
        public void generate(MessageMetaContext messageMetaContext, MessageMetadata messageMetadata) {
            Map<String, Object> headers = messageMetaContext.getHeaders();
            if (null == headers) {
                headers = new HashMap<>();
            }
            headers.put(CUSTOM_HEADER_KEY, CUSTOM_HEADER_VALUE);
            messageMetadata.setHeaders(headers);
        }
    }

}