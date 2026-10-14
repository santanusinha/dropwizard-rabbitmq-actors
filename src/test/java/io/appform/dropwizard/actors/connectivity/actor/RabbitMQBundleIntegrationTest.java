package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.RabbitmqActorBundle;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.junit.extension.RabbitMQExtension;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.utils.RMQTestUtils;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;

/**
 * Brings up a whole dropwizard application with {@link RabbitmqActorBundle}
 * registered and verifies real publish/consume against a live RabbitMQ broker through a bundle-managed actor.
 */
@Slf4j
@ExtendWith(RabbitMQExtension.class)
public class RabbitMQBundleIntegrationTest {

    private static final String TEST_EXCHANGE = "test-bundle-exchange";

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleIntegrationApp.class);

    @BeforeAll
    @SneakyThrows
    public static void beforeAll() {
        System.setProperty("dw.server.applicationConnectors[0].port", "0");
        System.setProperty("dw.server.adminConnectors[0].port", "0");
    }

    @AfterAll
    @SneakyThrows
    public static void afterAll() {
        System.clearProperty("dw.server.applicationConnectors[0].port");
        System.clearProperty("dw.server.adminConnectors[0].port");
    }

    @BeforeEach
    void setUp(final RabbitMQContainer rabbitMQContainer) throws Exception {
        final RMQConfig rmqConfig = RMQTestUtils.getRMQConfig(rabbitMQContainer);
        rmqConfig.setThreadPoolSize(1);
        RabbitMQBundleIntegrationApp.setRmqConfig(rmqConfig);
        app.before();
    }

    @Test
    void testBundleManagedPublishAndConsume() throws Exception {
        val integrationApp = (RabbitMQBundleIntegrationApp) app.getApplication();
        val actorBundle = integrationApp.getActorBundle();
        Assertions.assertNotNull(actorBundle);
        Assertions.assertNotNull(actorBundle.getConnectionRegistry());

        val objectMapper = new ObjectMapper();
        val retryStrategyFactory = new RetryStrategyFactory();
        val exceptionHandlingFactory = new ExceptionHandlingFactory();

        val receivedMessage = new AtomicReference<TestBundleMessage>(null);

        val actorConfig = ActorConfig.builder()
                .exchange(TEST_EXCHANGE)
                .concurrency(1)
                .prefetchCount(1)
                .build();

        val actor = new Actor<TestBundleMessage.Type, TestBundleMessage>(
                TestBundleMessage.Type.TEST_MESSAGE,
                actorConfig,
                actorBundle.getConnectionRegistry(),
                objectMapper,
                retryStrategyFactory,
                exceptionHandlingFactory,
                TestBundleMessage.class,
                Set.of()) {
            @Override
            protected boolean handle(final TestBundleMessage message, final MessageMetadata messageMetadata) {
                log.info("Consumed message: {}", message);
                receivedMessage.set(message);
                return true;
            }
        };
        actor.start();

        val message = TestBundleMessage.builder().name("bundle-integration-test").build();
        actor.publish(message);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    val received = receivedMessage.get();
                    Assertions.assertNotNull(received, "Message was not consumed");
                    Assertions.assertEquals(message.getName(), received.getName());
                });

        actor.stop();
    }
}
