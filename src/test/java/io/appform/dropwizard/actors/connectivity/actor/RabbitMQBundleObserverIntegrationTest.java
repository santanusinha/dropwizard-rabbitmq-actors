package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.RabbitmqActorBundle;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.config.MetricConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.junit.extension.RabbitMQExtension;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.utils.RMQTestUtils;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
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

/**
 * Verifies that a registered observer sees real publish/consume flows and that RMQ metrics
 * are recorded, all through a bundle-managed actor running inside a real dropwizard app.
 */
@Slf4j
@ExtendWith(RabbitMQExtension.class)
public class RabbitMQBundleObserverIntegrationTest {

    private static final String TEST_EXCHANGE = "test-observer-exchange";

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleObserverTestApp.class);

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
        rmqConfig.setMetricConfig(MetricConfig.builder().enabledForAll(true).build());
        RabbitMQBundleObserverTestApp.setRmqConfig(rmqConfig);
        app.before();
    }

    @Test
    void testObserverChainAndMetricsOnRealPublishConsume() throws Exception {
        val integrationApp = (RabbitMQBundleObserverTestApp) app.getApplication();
        val actorBundle = integrationApp.getActorBundle();
        val recordingObserver = integrationApp.getRecordingObserver();
        Assertions.assertNotNull(actorBundle);
        Assertions.assertNotNull(recordingObserver);

        val objectMapper = new ObjectMapper();
        val actorConfig = ActorConfig.builder()
                .exchange(TEST_EXCHANGE)
                .concurrency(1)
                .prefetchCount(1)
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
                receivedMessage.set(message);
                return true;
            }
        };
        actor.start();

        val message = TestBundleMessage.builder().name("observer-test").build();
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

        // Observer chain: publish and consume contexts recorded with expected queue names
        val expectedQueueName = actor.getActorImpl().getPublishActor().getQueueName();
        Assertions.assertFalse(recordingObserver.getPublishQueueNames().isEmpty(),
                "Publish observer context was not invoked");
        Assertions.assertEquals(expectedQueueName, recordingObserver.getPublishQueueNames().get(0));
        Assertions.assertFalse(recordingObserver.getConsumeQueueNames().isEmpty(),
                "Consume observer context was not invoked");
        Assertions.assertEquals(expectedQueueName, recordingObserver.getConsumeQueueNames().get(0));

        // Terminal observer actually published (message was consumed through the chain)
        Assertions.assertNotNull(receivedMessage.get());

        // RMQ metrics recorded via RMQMetricObserver for this queue
        val metricRegistry = app.getEnvironment().metrics();
        val publishTimerName = "rmq.actor." + expectedQueueName.replace('.', '_') + ".publish.latency";
        val consumeTimerName = "rmq.actor." + expectedQueueName.replace('.', '_') + ".consume.latency";
        val publishTotalName = "rmq.actor." + expectedQueueName.replace('.', '_') + ".publish.total";
        val consumeSuccessName = "rmq.actor." + expectedQueueName.replace('.', '_') + ".consume.success";
        Assertions.assertTrue(metricRegistry.getTimers().containsKey(publishTimerName),
                "Publish latency timer not registered: " + publishTimerName);
        Assertions.assertTrue(metricRegistry.getTimers().containsKey(consumeTimerName),
                "Consume latency timer not registered: " + consumeTimerName);
        Assertions.assertTrue(metricRegistry.getMeters().containsKey(publishTotalName),
                "Publish total meter not registered: " + publishTotalName);
        Assertions.assertTrue(metricRegistry.getMeters().containsKey(consumeSuccessName),
                "Consume success meter not registered: " + consumeSuccessName);
        Assertions.assertTrue(metricRegistry.getMeters().get(publishTotalName).getCount() >= 1,
                "Publish total meter not incremented");
        Assertions.assertTrue(metricRegistry.getMeters().get(consumeSuccessName).getCount() >= 1,
                "Consume success meter not incremented");
    }
}
