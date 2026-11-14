package io.appform.dropwizard.actors.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.handlers.MessageSidelineHandler;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.impl.NoRetryStrategy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A consumer channel closed by the broker alone is re-created; one closed by {@link UnmanagedConsumer#stop()}, by the
 * application or by a connection failure (left to automatic recovery) is not.
 */
class UnmanagedConsumerChannelRecoveryTest {

    private static final int RECREATION_WAIT_MS = 500;

    private RMQConnection connection;
    private final List<Channel> channels = new CopyOnWriteArrayList<>();
    private final List<ShutdownListener> listeners = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        connection = mock(RMQConnection.class);
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.newChannel()).thenAnswer(invocation -> newChannel());
    }

    private Channel newChannel() throws Exception {
        final Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        when(channel.basicConsume(anyString(), anyBoolean(), anyString(), any(Consumer.class)))
                .thenReturn("tag-" + channels.size());
        doAnswer(invocation -> {
            listeners.add(invocation.getArgument(0));
            return null;
        }).when(channel).addShutdownListener(any());
        doAnswer(invocation -> {
            // A real close notifies the channel's shutdown listeners, as initiated by the application.
            when(channel.isOpen()).thenReturn(false);
            fire(listeners.get(channels.indexOf(channel)), true, false);
            return null;
        }).when(channel).close();
        channels.add(channel);
        return channel;
    }

    private static void fire(final ShutdownListener listener, final boolean byApplication, final boolean hardError) {
        listener.shutdownCompleted(new ShutdownSignalException(hardError, byApplication, null, null));
    }

    private UnmanagedConsumer<String> startedConsumer(final int concurrency) throws Exception {
        final UnmanagedConsumer<String> consumer = new UnmanagedConsumer<>("q", 1, concurrency, false, 0, null,
                connection, new ObjectMapper(), new NoRetryStrategy(),
                new MessageSidelineHandler(null), String.class, (message, metadata) -> true,
                (message, metadata) -> true, t -> false);
        consumer.start();
        return consumer;
    }

    @Test
    void stopDoesNotSubscribeAgain() throws Exception {
        final UnmanagedConsumer<String> consumer = startedConsumer(2);
        verify(connection, times(2)).newChannel();

        consumer.stop();

        for (final Channel channel : channels) {
            verify(channel).close();
        }
        verify(connection, after(RECREATION_WAIT_MS).times(2)).newChannel();
        // No channel opened after stop(), and each consumer channel subscribed once (at start), never again.
        assertEquals(2, channels.size());
        for (final Channel channel : channels) {
            verify(channel, times(1)).basicConsume(anyString(), anyBoolean(), anyString(), any(Consumer.class));
        }
    }

    @Test
    void channelClosedByBrokerIsRecreated() throws Exception {
        startedConsumer(1);

        fire(listeners.get(0), false, false);

        verify(connection, timeout(RECREATION_WAIT_MS).times(2)).newChannel();
        verify(channels.get(1), timeout(RECREATION_WAIT_MS))
                .basicConsume(eq("q"), anyBoolean(), anyString(), any(Consumer.class));
    }

    @Test
    void channelClosedByConnectionFailureIsLeftToAutomaticRecovery() throws Exception {
        startedConsumer(1);

        fire(listeners.get(0), false, true);

        verify(connection, after(RECREATION_WAIT_MS).times(1)).newChannel();
    }

    @Test
    void channelClosedByApplicationIsNotRecreated() throws Exception {
        startedConsumer(1);

        fire(listeners.get(0), true, false);

        verify(connection, after(RECREATION_WAIT_MS).times(1)).newChannel();
    }

    @Test
    void closeAfterStopIsIgnored() throws Exception {
        final UnmanagedConsumer<String> consumer = startedConsumer(1);
        consumer.stop();

        fire(listeners.get(0), false, false);

        verify(connection, after(RECREATION_WAIT_MS).times(1)).newChannel();
    }

    @Test
    void channelRecreatedWhileStoppingIsCancelled() throws Exception {
        final AtomicReference<UnmanagedConsumer<String>> consumer = new AtomicReference<>();
        consumer.set(startedConsumer(1));

        // The broker closes the channel; while it is being re-created, stop() runs and can't see the new channel yet.
        final Channel late = mock(Channel.class);
        when(late.isOpen()).thenReturn(true);
        when(late.basicConsume(anyString(), anyBoolean(), anyString(), any(Consumer.class))).thenAnswer(invocation -> {
            consumer.get().stop();
            return "late-tag";
        });
        when(connection.newChannel()).thenReturn(late);
        fire(listeners.get(0), false, false);

        verify(late, timeout(RECREATION_WAIT_MS)).basicCancel("late-tag");
        verify(late, timeout(RECREATION_WAIT_MS)).close();
    }
}
