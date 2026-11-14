package io.appform.dropwizard.actors.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.messaging.MessagingNames.ExchangeNames;
import io.appform.dropwizard.actors.messaging.config.ExchangeSpec;
import io.appform.dropwizard.actors.messaging.config.QueueSpec;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The declaration channel must outlive {@code declare()}: the client's automatic topology recovery redeclares each
 * recorded entity on the channel it was declared on, so closing it makes every redeclare fail after a reconnect.
 */
class TopologyDeclarerTest {

    private static final ExchangeNames NAMES = new ExchangeNames("ex", "ex", "ex_SIDELINE");
    private static final ExchangeSpec SPEC = ExchangeSpec.builder().type(ExchangeType.DIRECT).build();

    @Test
    void keepsDeclarationChannelOpenUntilClosed() throws Exception {
        final Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        final RMQConnection connection = mock(RMQConnection.class);
        when(connection.newChannel()).thenReturn(channel);
        final TopologyDeclarer declarer = new TopologyDeclarer("p", mock(BindingGuard.class));

        declarer.declare(NAMES, SPEC, connection);
        verify(channel, never()).close();

        declarer.close();
        verify(channel).close();
    }

    @Test
    void closesChannelWhenDeclarationFails() throws Exception {
        final Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        doThrow(new IOException("406")).when(channel).exchangeDeclare(anyString(), anyString(), anyBoolean());
        final RMQConnection connection = mock(RMQConnection.class);
        when(connection.newChannel()).thenReturn(channel);
        final TopologyDeclarer declarer = new TopologyDeclarer("p", mock(BindingGuard.class));

        assertThrows(IOException.class, () -> declarer.declare(NAMES, SPEC, connection));
        verify(channel).close();

        declarer.close();
        verify(channel).close(); // not closed a second time
    }

    @Test
    void driftRejectionDeclaresNothingForTheQueue() throws Exception {
        final Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        final RMQConnection connection = mock(RMQConnection.class);
        when(connection.newChannel()).thenReturn(channel);
        when(connection.rmqOpts(any())).thenReturn(new HashMap<>());
        final BindingGuard guard = mock(BindingGuard.class);
        doThrow(new IllegalStateException("drift")).when(guard)
                .ensureBindings(anyString(), anyString(), any(), any(), any(), any());
        final ExchangeSpec spec = ExchangeSpec.builder()
                .type(ExchangeType.DIRECT)
                .queues(new LinkedHashMap<>(Map.of("Q",
                        QueueSpec.builder()
                                .bindingKeys(List.of("k")).build())))
                .build();
        final TopologyDeclarer declarer = new TopologyDeclarer("p", guard);

        assertThrows(IllegalStateException.class, () -> declarer.declare(NAMES, spec, connection));
        // The library never unbinds, so the sideline queue must not be declared or bound before the guard passes.
        verify(channel, never()).queueDeclare(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), any());
        verify(channel, never()).queueBind(anyString(), anyString(), anyString());
    }

    @Test
    void ownRetryExchangeIsAllowedEvenWithRetryLaterDisabled() throws Exception {
        final Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        final RMQConnection connection = mock(RMQConnection.class);
        when(connection.newChannel()).thenReturn(channel);
        when(connection.rmqOpts(any())).thenReturn(new HashMap<>());
        final BindingGuard guard = mock(BindingGuard.class);
        final ExchangeSpec spec = ExchangeSpec.builder()
                .type(ExchangeType.DIRECT)
                .queues(new LinkedHashMap<>(Map.of("Q",
                        QueueSpec.builder()
                                .bindingKeys(List.of("k"))
                                .retryLaterEnabled(false)
                                .build())))
                .build();

        new TopologyDeclarer("p", guard).declare(NAMES, spec, connection);

        final String brokerQueue = MessagingNames.queue("p", "Q");
        // A queue that had retryLater enabled before keeps its retry binding: it must not fail the drift check.
        verify(guard).ensureBindings(eq(brokerQueue), eq("ex"), eq(List.of("k")),
                eq(Set.of(MessagingNames.retryExchange(brokerQueue))), any(), any());
        verify(channel, never()).exchangeDeclare(eq(MessagingNames.retryExchange(brokerQueue)), anyString(),
                anyBoolean(), anyBoolean(), any());
    }

    @Test
    void declarationFinishedReleasesGuardButKeepsChannelsOpen() throws Exception {
        final Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        final RMQConnection connection = mock(RMQConnection.class);
        when(connection.newChannel()).thenReturn(channel);
        final BindingGuard guard = mock(BindingGuard.class);
        final TopologyDeclarer declarer = new TopologyDeclarer("p", guard);
        declarer.declare(NAMES, SPEC, connection);

        declarer.declarationFinished();

        verify(guard).close();
        verify(channel, never()).close();

        declarer.close();
        verify(channel).close();
    }
}
