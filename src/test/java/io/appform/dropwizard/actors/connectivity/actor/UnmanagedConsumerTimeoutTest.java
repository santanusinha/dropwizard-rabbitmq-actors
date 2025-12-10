package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;

@Slf4j
@ExtendWith(RabbitMQExtension.class)
public class UnmanagedConsumerTimeoutTest {

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app = new DropwizardAppExtension<>(
            RabbitMQBundleTestApplication.class);
    public static final String TEST_EXCHANGE = "test-exchange";
    private static RMQConnection connection;

    @BeforeEach
    @SneakyThrows
    public void beforeMethod(final RabbitMQContainer rabbitMQContainer) {
        app.before();

        RMQConfig config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        connection = new RMQConnection("test-conn", config, Executors.newSingleThreadExecutor(), app.getEnvironment(),
                TtlConfig.builder()
                        .build(), new TerminalRMQObserver());
        connection.start();
    }

    @AfterEach
    @SneakyThrows
    public void afterMethod() {
        app.after();
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
        String queueName = "test-queue-timeout";
        UnmanagedPublisher<Object> publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection,
                objectMapper);
        publisher.start();

        ImmutableMap<String, String> message = ImmutableMap.of("key", "test-message");
        publisher.publish(message);

        AtomicInteger seen = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(2);
        Thread t = new Thread(() -> {
            try {
                UnmanagedConsumer<Map> consumer = new UnmanagedConsumer<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig.getPrefetchCount(), actorConfig.getConcurrency(),actorConfig.isSharded(), actorConfig.getShardCount() , actorConfig.getConsumer(), connection,
                        objectMapper, new RetryStrategyFactory().create(actorConfig.getRetryConfig()), new ExceptionHandlingFactory().create(actorConfig.getExceptionHandlerConfig()), Map.class,
                        (msg, metadata) -> {
                            testDataHolder.get()
                                    .put(seen.incrementAndGet(), metadata);
                            if (seen.get() == 1) { // only sleep for the first time to induce consumer timeout
                                // Thread.sleep() is set to a value more than 2 times the consumer_timeout to avoid test flakiness
                                // as the RMQ periodically checks for consumer timeout
                                Thread.sleep((long) (RMQTestUtils.CONSUMER_TIMEOUT_MS * 2.2));
                            }
                            latch.countDown();
                            log.info("Processed message {}", msg);
                            return true;
                        }, (x, y) -> true, (x) -> true);
                consumer.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        // set timeout to ensure the tests do not hang
        latch.await(RMQTestUtils.CONSUMER_TIMEOUT_MS * 3, TimeUnit.MILLISECONDS);
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
