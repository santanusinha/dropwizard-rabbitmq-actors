package io.appform.dropwizard.actors.retry;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.async.RetrySchedulingRejectedException;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import io.appform.dropwizard.actors.retry.impl.NoRetryStrategy;
import io.dropwizard.util.Duration;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetryStrategyFactoryTest {

    @Test
    void syncCreationDoesNotRequireTransportContext() throws Exception {
        var config = new NoRetryConfig();
        var strategy = new RetryStrategyFactory().create(config, null, null, null, null);
        strategy.start();
        assertTrue(strategy.execute(() -> true, null, null, null));
        strategy.stop();
    }

    @Test
    void nullWaitModeUsesSynchronousExecution() throws Exception {
        var config = new NoRetryConfig();
        config.setWaitMode(null);
        assertTrue(new RetryStrategyFactory().create(config, null, null, null, null)
                .execute(() -> true, null, null, null));
    }

    @Test
    void contextualCreationUsesStrategyExtensionPoint() {
        var config = new NoRetryConfig();
        var customStrategy = new NoRetryStrategy();
        var factory = new RetryStrategyFactory() {
            @Override
            protected RetryStrategy createStrategy(RetryConfig actual) {
                assertSame(config, actual);
                return customStrategy;
            }
        };
        assertSame(customStrategy, factory.create(config, null, null, null, null));
    }

    @Test
    void actorCreationWiresTopologyAndConfirmedRetryPublisher() throws Exception {
        var retry = new CountLimitedFixedWaitRetryConfig();
        retry.setWaitMode(WaitMode.ASYNC);
        retry.setMaxAttempts(3);
        retry.setWaitTime(Duration.milliseconds(100));
        var actorConfig = ActorConfig.builder().exchange("exchange").retryConfig(retry).build();
        var connection = mock(RMQConnection.class);
        var topology = mock(Channel.class);
        var publisher = mock(Channel.class);
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.newChannel()).thenReturn(topology, publisher);
        when(publisher.isOpen()).thenReturn(true);
        var strategy = new RetryStrategyFactory().create(actorConfig, connection, "queue");
        var waiting = "exchange_RETRY_classic_100";
        strategy.start();
        try {
            verify(topology).exchangeDeclare(waiting, "fanout", true);
            verify(connection).ensure(topology, waiting, "", waiting,
                    Map.of("x-queue-type", "classic", "x-message-ttl", 100L, "x-dead-letter-exchange", "exchange"));
            var body = new byte[]{1};
            assertTrue(strategy.execute(() -> {
                throw new IOException("business failure");
            }, "queue", body, new AMQP.BasicProperties()));
            var properties = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
            verify(publisher).basicPublish(eq(waiting), eq("queue"), eq(true), properties.capture(), same(body));
            assertEquals(2L, properties.getValue().getHeaders().get("x-actor-retry-attempt"));
            verify(publisher).confirmSelect();
            verify(publisher).waitForConfirmsOrDie(RetryStrategyFactory.DEFAULT_CONFIRM_TIMEOUT_MS);
        } finally {
            strategy.stop();
        }
        verify(publisher).close();
        verify(topology).close();
    }

    @Test
    void differentConsumerQueuesDeclareTheSameWaitingQueueForTheSameDelay() throws Exception {
        var retry = new CountLimitedFixedWaitRetryConfig();
        retry.setWaitMode(WaitMode.ASYNC);
        retry.setMaxAttempts(3);
        retry.setWaitTime(Duration.milliseconds(100));
        var actorConfig = ActorConfig.builder().exchange("exchange").retryConfig(retry).build();
        var connection = mock(RMQConnection.class);
        var topology = mock(Channel.class);
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.newChannel()).thenReturn(topology);
        var factory = new RetryStrategyFactory();
        var first = factory.create(actorConfig, connection, "first-queue");
        var second = factory.create(actorConfig, connection, "second-queue");
        try {
            first.start();
            second.start();
            var waiting = "exchange_RETRY_classic_100";
            verify(topology, times(2)).exchangeDeclare(waiting, "fanout", true);
            verify(connection, times(2)).ensure(topology, waiting, "", waiting,
                    Map.of("x-queue-type", "classic", "x-message-ttl", 100L, "x-dead-letter-exchange", "exchange"));
        } finally {
            first.stop();
            second.stop();
        }
    }

    @Test
    void asyncStartRejectsPoliciesBeyondPlanningBudgetBeforeDeclaringTopology() throws Exception {
        var retry = new CountLimitedFixedWaitRetryConfig();
        retry.setWaitMode(WaitMode.ASYNC);
        retry.setMaxAttempts(11);
        var connection = mock(RMQConnection.class);
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        var strategy = new RetryStrategyFactory().create(retry, new ActorConfig(), connection, "exchange", "queue");
        assertThrows(RetrySchedulingRejectedException.class, strategy::start);
        verify(connection, never()).newChannel();
    }
}
