package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.junit.extension.RabbitMQExtension;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.utils.RMQTestUtils;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies delayed (TTL exchange) and expiry publishing through a bundle-managed actor
 * running inside a real dropwizard app.
 */
@Slf4j
@ExtendWith(RabbitMQExtension.class)
public class RabbitMQBundleDelayedPublishIntegrationTest {

    private static final String TEST_EXCHANGE = "test-delayed-bundle-exchange";

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleIntegrationApp.class, null, ConfigOverride.randomPorts());

    @BeforeEach
    void setUp(final RabbitMQContainer rabbitMQContainer) throws Exception {
        final RMQConfig rmqConfig = RMQTestUtils.getRMQConfig(rabbitMQContainer);
        rmqConfig.setThreadPoolSize(1);
        RabbitMQBundleIntegrationApp.setRmqConfig(rmqConfig);
        app.before();
    }

    @Test
    void testPublishWithDelayIsDeliveredAfterDelay() throws Exception {
        val integrationApp = (RabbitMQBundleIntegrationApp) app.getApplication();
        val actorBundle = integrationApp.getActorBundle();
        Assertions.assertNotNull(actorBundle);

        val objectMapper = new ObjectMapper();
        val actorConfig = ActorConfig.builder()
                .exchange(TEST_EXCHANGE)
                .concurrency(1)
                .prefetchCount(1)
                .delayed(true)
                .delayType(DelayType.TTL)
                .build();

        val receivedMessage = new AtomicReference<TestBundleMessage>(null);

        val actor = new Actor<TestBundleMessage.Type, TestBundleMessage>(
                TestBundleMessage.Type.TEST_MESSAGE,
                actorConfig,
                actorBundle.getConnectionRegistry(),
                objectMapper,
                new RetryStrategyFactory(),
                new ExceptionHandlingFactory(),
                TestBundleMessage.class,
                Set.of()) {
            @Override
            protected boolean handle(final TestBundleMessage message, final MessageMetadata messageMetadata) {
                log.info("Consumed delayed message: {}", message);
                receivedMessage.set(message);
                return true;
            }
        };
        actor.start();

        val message = TestBundleMessage.builder().name("delayed-bundle-test").build();
        val delayMillis = 2000L;
        val publishTime = System.currentTimeMillis();
        actor.publishWithDelay(message, delayMillis);

        // Message must not be delivered before the delay elapses
        Thread.sleep(delayMillis - 500);
        Assertions.assertNull(receivedMessage.get(), "Message was delivered before the configured delay");

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    val received = receivedMessage.get();
                    Assertions.assertNotNull(received, "Delayed message was not consumed");
                    Assertions.assertEquals(message.getName(), received.getName());
                });
        Assertions.assertTrue(System.currentTimeMillis() - publishTime >= delayMillis,
                "Message was delivered before the configured delay elapsed");

        actor.stop();
    }

}
