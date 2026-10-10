package io.appform.dropwizard.actors.retry;

import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.retry.async.AsyncRetryScheduler;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetryStrategyFactoryTest {

    @Test
    void syncDoesNotConstructSchedulerOrRequireTransportContext() throws Exception {
        var calls = new AtomicInteger();
        var factory = new RetryStrategyFactory() {
            @Override
            protected AsyncRetryScheduler createScheduler(RetryConfig config, ActorConfig actorConfig,
                                                            RMQConnection connection, String exchange, String queueName) {
                calls.incrementAndGet();
                throw new AssertionError("Sync must not construct retry transport");
            }
        };

        var strategy = factory.create(new NoRetryConfig(), null, null, null, null);

        assertTrue(strategy.execute(() -> true, null, null, null));
        assertEquals(0, calls.get());
    }

    @Test
    void contextualAsyncCreationUsesOverriddenSchedulerHook() throws Exception {
        var scheduler = mock(AsyncRetryScheduler.class);
        var config = new NoRetryConfig();
        config.setRetryMode(RetryMode.ASYNC);
        var actorConfig = new ActorConfig();
        var connection = mock(RMQConnection.class);
        var factory = new RetryStrategyFactory() {
            @Override
            protected AsyncRetryScheduler createScheduler(RetryConfig actualConfig, ActorConfig actualActorConfig,
                                                            RMQConnection actualConnection, String exchange, String queueName) {
                assertSame(config, actualConfig);
                assertSame(actorConfig, actualActorConfig);
                assertSame(connection, actualConnection);
                assertEquals("exchange", exchange);
                assertEquals("queue", queueName);
                return scheduler;
            }
        };

        var strategy = factory.create(config, actorConfig, connection, "exchange", "queue");
        strategy.start();
        strategy.stop();

        verify(scheduler).start();
        verify(scheduler).stop();
        verifyNoInteractions(connection);
    }

    @Test
    void nullModeFailsBeforeConstructingTransport() {
        var config = new NoRetryConfig();
        config.setRetryMode(null);

        var error = assertThrows(NullPointerException.class,
                () -> new RetryStrategyFactory().create(config, null, null, null, null));

        assertEquals("retryMode", error.getMessage());
    }
}
