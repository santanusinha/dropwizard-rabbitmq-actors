package io.appform.dropwizard.actors.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.messaging.config.ExchangeSpec;
import io.appform.dropwizard.actors.messaging.config.MessagingConfig;
import io.appform.dropwizard.actors.messaging.config.QueueSpec;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.WaitMode;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MessagingTest {

    private static final QueueHandler<String> HANDLER = (message, context) -> true;

    private RMQConnection connection;
    private Channel channel;
    private ConnectionRegistry registry;
    private TopologyDeclarer declarer;

    @BeforeEach
    void setUp() throws Exception {
        channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        connection = mock(RMQConnection.class);
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.newChannel()).thenReturn(channel);
        registry = mock(ConnectionRegistry.class);
        when(registry.createOrGet(anyString())).thenReturn(connection);
        declarer = mock(TopologyDeclarer.class);
    }

    private static MessagingConfig config() {
        final Map<String, ExchangeSpec> exchanges = new LinkedHashMap<>();
        exchanges.put("orders", ExchangeSpec.builder()
                .type(ExchangeType.TOPIC)
                .queues(new LinkedHashMap<>(Map.of("shipping",
                        QueueSpec.builder().bindingKeys(List.of("order.#")).concurrency(1).build())))
                .build());
        exchanges.put("broadcast", ExchangeSpec.builder()
                .type(ExchangeType.FANOUT)
                .queues(new LinkedHashMap<>(Map.of("audit", QueueSpec.builder().concurrency(1).build())))
                .build());
        return MessagingConfig.builder().prefix("p").exchanges(exchanges).build();
    }

    private Messaging messaging() {
        return new Messaging(config(), registry, new ObjectMapper(), new RetryStrategyFactory(),
                new ExceptionHandlingFactory(), declarer);
    }

    private Messaging fullyRegistered() {
        final Messaging messaging = messaging();
        messaging.publisher("orders", (String m) -> "order." + m);
        messaging.publisher("broadcast");
        messaging.consumer("orders", "shipping", String.class, HANDLER);
        messaging.consumer("broadcast", "audit", String.class, HANDLER);
        return messaging;
    }

    @Test
    void invalidConfigFailsConstructionWithAllErrors() {
        final MessagingConfig bad = MessagingConfig.builder().exchanges(new LinkedHashMap<>(Map.of(
                "e_SIDELINE", ExchangeSpec.builder().type(ExchangeType.DIRECT).build()))).build();
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new Messaging(bad,
                registry, new ObjectMapper(), new RetryStrategyFactory(), new ExceptionHandlingFactory(), declarer));
        assertTrue(e.getMessage().startsWith("Invalid messaging config: ["), e.getMessage());
        assertTrue(e.getMessage().contains("reserved") && e.getMessage().contains("no queues"), e.getMessage());
    }

    @Test
    void registrationValidation() {
        final Messaging messaging = messaging();
        assertThrows(IllegalArgumentException.class, () -> messaging.publisher("missing"));
        assertThrows(IllegalArgumentException.class, () -> messaging.publisher("orders"));
        assertThrows(IllegalArgumentException.class, () -> messaging.publisher("broadcast", (String m) -> m));
        assertThrows(NullPointerException.class, () -> messaging.publisher("orders", null));
        assertThrows(IllegalArgumentException.class,
                () -> messaging.consumer("orders", "audit", String.class, HANDLER));
        assertThrows(IllegalArgumentException.class,
                () -> messaging.consumer("missing", "shipping", String.class, HANDLER));
        messaging.consumer("orders", "shipping", String.class, HANDLER);
        assertThrows(IllegalArgumentException.class,
                () -> messaging.consumer("orders", "shipping", String.class, HANDLER));
    }

    @Test
    void startFailsBeforeTouchingBrokerWhenQueueHasNoHandler() throws Exception {
        final Messaging messaging = messaging();
        messaging.consumer("orders", "shipping", String.class, HANDLER);
        final IllegalStateException e = assertThrows(IllegalStateException.class, messaging::start);
        assertTrue(e.getMessage().contains("[audit]"), e.getMessage());
        verify(declarer, never()).declare(any(), any(), any());
        verify(connection, never()).newChannel();
    }

    @Test
    void startDeclaresTopologyAndStartsPublishersButNotConsumers() throws Exception {
        final Messaging messaging = fullyRegistered();
        messaging.start();

        final InOrder order = inOrder(declarer, channel);
        order.verify(declarer, times(2)).declare(any(), any(), eq(connection));
        // The Management API client is released once the topology is declared, before anything else starts.
        order.verify(declarer).declarationFinished();
        order.verify(channel, times(2)).confirmSelect();
        verify(channel, never()).basicConsume(anyString(), anyBoolean(), anyString(), any(Consumer.class));
        // Messaging never uses the connection's shared channel, which actors use.
        verify(connection, never()).channel();

        assertThrows(IllegalStateException.class, () -> messaging.publisher("broadcast"));
        assertThrows(IllegalStateException.class, messaging::start);
    }

    @Test
    void startConsumersSubscribesEveryQueueOnce() throws Exception {
        final Messaging messaging = fullyRegistered();
        messaging.start();
        messaging.startConsumers();
        messaging.startConsumers();

        verify(channel, times(2)).basicConsume(anyString(), anyBoolean(), anyString(), any(Consumer.class));
    }

    @Test
    void startConsumersRequiresStart() {
        final Messaging messaging = fullyRegistered();
        assertThrows(IllegalStateException.class, messaging::startConsumers);
    }

    @Test
    void stopConsumersKeepsPublishersAndPreventsRestart() throws Exception {
        final Messaging messaging = fullyRegistered();
        messaging.start();
        messaging.startConsumers();

        messaging.stopConsumers();
        messaging.stopConsumers();

        verify(channel, times(2)).basicCancel(any());
        // Publisher channels are still open: only the consumer channels were closed.
        verify(channel, times(2)).close();
        final IllegalStateException e = assertThrows(IllegalStateException.class, messaging::startConsumers);
        assertTrue(e.getMessage().contains("can't be restarted"), e.getMessage());

        messaging.stop();
        verify(channel, times(2)).basicCancel(any());
        verify(channel, times(4)).close();
    }

    @Test
    void stopAlsoStopsRunningConsumers() throws Exception {
        final Messaging messaging = fullyRegistered();
        messaging.start();
        messaging.startConsumers();

        messaging.stop();

        verify(channel, times(2)).basicCancel(any());
        verify(declarer).close();
    }

    @Test
    void declarationFailureStopsNothingStartedAndClosesDeclarer() throws Exception {
        final Messaging messaging = fullyRegistered();
        doThrow(new IOException("406 PRECONDITION_FAILED")).when(declarer).declare(any(), any(), any());

        assertThrows(IOException.class, messaging::start);

        verify(declarer).declarationFinished();
        verify(channel, never()).confirmSelect();
        verify(declarer).close();
        verify(connection, never()).channel();
        final IllegalStateException e = assertThrows(IllegalStateException.class, messaging::start);
        assertTrue(e.getMessage().contains("can't be restarted"), e.getMessage());
    }

    @Test
    void consumerStartFailureStopsStartedConsumersButKeepsPublishers() throws Exception {
        final Messaging messaging = fullyRegistered();
        messaging.start();
        when(channel.basicConsume(anyString(), anyBoolean(), anyString(), any(Consumer.class)))
                .thenReturn("tag")
                .thenThrow(new IOException("404 NOT_FOUND"));

        assertThrows(IOException.class, messaging::startConsumers);

        // The first queue's consumer channel is cancelled and closed; publishers stay up until stop().
        verify(channel).basicCancel("tag");
        verify(channel, times(1)).close();
        verify(declarer, never()).close();
        assertThrows(IllegalStateException.class, messaging::startConsumers);

        messaging.stop();
        verify(declarer).close();
    }

    @Test
    void stopIsIdempotentAndPreventsRestart() throws Exception {
        final Messaging messaging = fullyRegistered();
        messaging.start();
        messaging.stop();
        messaging.stop();

        verify(declarer, times(1)).close();
        assertThrows(IllegalStateException.class, messaging::start);
        assertThrows(IllegalStateException.class, () -> messaging.publisher("broadcast"));
    }

    @Test
    void asyncRetryWaitModeFailsConstruction() {
        final CountLimitedFixedWaitRetryConfig async =
                new CountLimitedFixedWaitRetryConfig();
        async.setWaitMode(WaitMode.ASYNC);
        final MessagingConfig config = MessagingConfig.builder().exchanges(new LinkedHashMap<>(Map.of("e",
                ExchangeSpec.builder().type(ExchangeType.FANOUT).queues(new LinkedHashMap<>(Map.of("q",
                        QueueSpec.builder().retryConfig(async).build()))).build()))).build();

        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new Messaging(config,
                registry, new ObjectMapper(), new RetryStrategyFactory(), new ExceptionHandlingFactory(), declarer));
        assertTrue(e.getMessage().contains("retryConfig.waitMode ASYNC is not supported"), e.getMessage());
    }
}

