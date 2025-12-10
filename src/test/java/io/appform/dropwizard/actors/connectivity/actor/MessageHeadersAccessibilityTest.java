package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.BasicProperties.Builder;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.base.UnmanagedConsumer;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;

@Slf4j
@ExtendWith(RabbitMQExtension.class)
public class MessageHeadersAccessibilityTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleTestApplication.class);
    private static RMQConnection connection;

    @BeforeEach
    @SneakyThrows
    public void beforeMethod(final RabbitMQContainer rabbitMQContainer) {
        System.setProperty("dw." + "server.applicationConnectors[0].port", "0");
        System.setProperty("dw." + "server.adminConnectors[0].port", "0");

        app.before();

        val config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        connection = new RMQConnection("test-conn", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build(), new TerminalRMQObserver());
        connection.start();

    }

    @AfterAll
    @SneakyThrows
    public static void afterMethod() {
        app.after();
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
        val publisher = new UnmanagedPublisher<>(
                NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        publisher.start();

        val message = ImmutableMap.of(
                "key", "test-message"
        );
        BasicProperties msgProperties = new Builder().headers(Map.of("test-header", "test-value")).build();
        publisher.publish(message, msgProperties);

        val consumer = new UnmanagedConsumer<>(
                NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig.getPrefetchCount(), actorConfig.getConcurrency(),actorConfig.isSharded(), actorConfig.getShardCount() , actorConfig.getConsumer(), connection,
                objectMapper, new RetryStrategyFactory().create(actorConfig.getRetryConfig()), new ExceptionHandlingFactory().create(actorConfig.getExceptionHandlerConfig()),
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
        MessageMetadata messageMetadata = (MessageMetadata) objMeta;
        Map<String, Object> msgHeaders = messageMetadata.getHeaders();
        Assertions.assertNotNull(msgHeaders);
        Assertions.assertTrue(StringUtils.equals("test-value", String.valueOf(msgHeaders.get("test-header"))));
    }

}
