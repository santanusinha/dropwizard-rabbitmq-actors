package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
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

/**
 * Verifies expiry publishing through the bundle: a message published with an expiry via a
 * publisher on the bundle's connection is delivered to the expired-message handler (not the
 * normal handler) of a bundle-managed actor inside a real dropwizard app.
 */
@Slf4j
@ExtendWith(RabbitMQExtension.class)
public class RabbitMQBundleExpiryPublishIntegrationTest {

    private static final String TEST_EXCHANGE = "test-expiry-bundle-exchange";

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
    void testPublishWithExpiryIsDeliveredToExpiredHandler() throws Exception {
        val integrationApp = (RabbitMQBundleIntegrationApp) app.getApplication();
        val actorBundle = integrationApp.getActorBundle();
        Assertions.assertNotNull(actorBundle);

        val objectMapper = new ObjectMapper();
        val actorConfig = ActorConfig.builder()
                .exchange(TEST_EXCHANGE)
                .concurrency(1)
                .prefetchCount(1)
                .build();

        // Publish with expiry on the bundle's producer connection, no consumer attached yet
        val producerConnection = actorBundle.getDefaultProducerConnection();
        val queueName = NamingUtils.queueName(actorConfig.getPrefix(), "TEST_MESSAGE");
        val publisher = new UnmanagedPublisher<>(queueName, actorConfig, producerConnection, objectMapper);
        publisher.start();
        val message = TestBundleMessage.builder().name("expiry-bundle-test").build();
        publisher.publishWithExpiry(message, 1500);
        publisher.stop();

        // Wait past expiry so the message is expired when the consuming actor attaches
        Thread.sleep(1510);

        val handledCount = new AtomicInteger();
        val expiredHandlerCount = new AtomicInteger();

        val consumerActor = new Actor<TestBundleMessage.Type, TestBundleMessage>(
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
                handledCount.incrementAndGet();
                return true;
            }

            @Override
            protected boolean handleExpiredMessages(final TestBundleMessage message,
                                                    final MessageMetadata messageMetadata) {
                log.info("Consumed expired message: {}", message);
                expiredHandlerCount.incrementAndGet();
                return true;
            }
        };
        consumerActor.start();

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        Assertions.assertEquals(1, expiredHandlerCount.get(),
                                "Expired message was not delivered to the expired handler"));
        Assertions.assertEquals(0, handledCount.get(),
                "Expired message must not be delivered to the normal handler");

        consumerActor.stop();
    }
}
