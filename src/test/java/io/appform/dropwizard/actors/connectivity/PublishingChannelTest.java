package io.appform.dropwizard.actors.connectivity;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Return;
import com.rabbitmq.client.ReturnCallback;
import io.appform.dropwizard.actors.observers.ConsumeMessageDetails;
import io.appform.dropwizard.actors.observers.ConsumeObserverContext;
import io.appform.dropwizard.actors.observers.PublishMessageDetails;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingChannelTest {

    private static final long CONFIRM_TIMEOUT_MS = 5000L;

    private static final ThreadLocal<Boolean> INSIDE_OBSERVER = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private RMQConnection connection;
    private RecordingObserver observer;
    private final List<Channel> channels = new ArrayList<>();
    private final List<ReturnCallback> returnCallbacks = new ArrayList<>();
    private final List<Published> published = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        observer = new RecordingObserver();
        connection = mock(RMQConnection.class);
        when(connection.getRootObserver()).thenReturn(observer);
        when(connection.newChannel()).thenAnswer(invocation -> newPublishingChannel());
    }

    @Test
    void plainPublishRunsInsideObserverAndAppliesTransform() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection);
        publishingChannel.start();

        final AMQP.BasicProperties rawProperties = new AMQP.BasicProperties.Builder()
                .headers(new HashMap<>(Map.of("raw", "true")))
                .build();
        final AMQP.BasicProperties enrichedProperties = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .build();
        final AtomicBoolean transformInsideObserver = new AtomicBoolean(false);
        final AtomicBoolean serializationInsideObserver = new AtomicBoolean(false);

        publishingChannel.publish("queue-1",
                "exchange-1",
                "rk-1",
                rawProperties,
                properties -> {
                    transformInsideObserver.set(INSIDE_OBSERVER.get());
                    return enrichedProperties;
                },
                () -> {
                    serializationInsideObserver.set(INSIDE_OBSERVER.get());
                    return "payload".getBytes(StandardCharsets.UTF_8);
                });

        assertEquals(1, published.size());
        final Published record = published.get(0);
        assertEquals("4", record.style);
        assertEquals("exchange-1", record.exchange);
        assertEquals("rk-1", record.routingKey);
        assertSame(enrichedProperties, record.properties);
        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), record.body);

        assertTrue(transformInsideObserver.get());
        assertTrue(serializationInsideObserver.get());

        assertEquals(1, observer.contexts.size());
        final PublishObserverContext context = observer.contexts.get(0);
        assertEquals("queue-1", context.getQueueName());
        assertSame(rawProperties, context.getMessageProperties());
        verify(channels.get(0), never()).confirmSelect();
        verify(channels.get(0), never()).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
    }

    @Test
    void plainPublishWithBodyUsesObserverProvidedProperties() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection);
        publishingChannel.start();

        final AMQP.BasicProperties rawProperties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .build();
        final byte[] body = {9, 8, 7};

        publishingChannel.publish("queue-1", "exchange-1", "rk-1", rawProperties, body);

        assertEquals(1, published.size());
        final Published record = published.get(0);
        assertEquals("4", record.style);
        assertEquals("exchange-1", record.exchange);
        assertEquals("rk-1", record.routingKey);
        assertSame(rawProperties, record.properties);
        assertArrayEquals(body, record.body);

        assertEquals(1, observer.contexts.size());
        assertSame(rawProperties, observer.contexts.get(0).getMessageProperties());
    }

    @Test
    void plainPublishWithoutStartThrowsIllegalState() {
        final PublishingChannel publishingChannel = new PublishingChannel(connection);

        assertThrows(IllegalStateException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{1}));

        assertTrue(published.isEmpty());
    }

    @Test
    void plainLifecycleStopsPublishingUntilRestart() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection);
        publishingChannel.start();
        publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{1});

        publishingChannel.close();
        verify(channels.get(0)).close();

        assertThrows(IllegalStateException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{2}));

        publishingChannel.start();
        publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{3});

        assertEquals(2, channels.size());
        assertEquals(2, published.size());
        assertEquals("4", published.get(1).style);
    }

    @Test
    void confirmedPublishIsMandatoryAndConfirmed() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        publishingChannel.start();

        publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{1});

        final Channel channel = channels.get(0);
        verify(channel).confirmSelect();
        assertEquals(1, returnCallbacks.size());
        verify(channel).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
        assertEquals(1, published.size());
        final Published record = published.get(0);
        assertEquals("5:true", record.style);
        assertEquals("exchange-1", record.exchange);
        assertEquals("rk-1", record.routingKey);
    }

    @Test
    void confirmedLifecycleBlocksPublishingUntilStartAfterClose() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        publishingChannel.start();
        publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{1});

        publishingChannel.close();
        verify(channels.get(0)).close();

        assertThrows(IllegalStateException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{2}));

        publishingChannel.start();
        publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{3});

        assertEquals(2, channels.size());
        assertEquals(2, published.size());
    }

    @Test
    void confirmedTimeoutDiscardsChannelAndExitsAsTimeoutException() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        publishingChannel.start();
        final Channel firstChannel = prepareChannel();
        doThrow(new TimeoutException()).when(firstChannel).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);

        assertThrows(TimeoutException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{1}));

        verify(firstChannel).close();
        assertFalse(observer.failures.isEmpty());
        assertTrue(observer.failures.stream().anyMatch(failure -> failure.getCause() instanceof TimeoutException));

        publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{2});

        assertEquals(2, channels.size());
        verify(channels.get(1)).confirmSelect();
        assertEquals(2, published.size());
    }

    @Test
    void confirmedInterruptExitsAsInterruptedAndRestoresInterruptFlag() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        publishingChannel.start();
        var channel = prepareChannel();
        doThrow(new InterruptedException()).when(channel).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);

        assertThrows(InterruptedException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{1}));

        assertTrue(Thread.interrupted());
        assertFalse(observer.failures.isEmpty());
        assertTrue(observer.failures.stream().anyMatch(failure -> failure.getCause() instanceof InterruptedException));
    }

    @Test
    void returnedMessageFailsPublishAsUnroutableAndDiscardsChannel() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        publishingChannel.start();
        final Channel channel = prepareChannel();
        final AMQP.BasicProperties properties = new AMQP.BasicProperties();
        doAnswer(invocation -> {
            returnCallbacks.get(0).handle(new Return(312, "NO_ROUTE", "exchange-1", "rk-1", properties, new byte[0]));
            return null;
        }).when(channel).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);

        final IOException error = assertThrows(IOException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        properties, p -> p, () -> new byte[]{1}));

        assertTrue(error.getMessage().contains("unroutable"));
        verify(channel).close();
        assertFalse(observer.failures.isEmpty());
    }

    @Test
    void staleReturnCallbacksDoNotContaminateReplacementChannel() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        publishingChannel.start();
        final Channel firstChannel = prepareChannel();
        doThrow(new TimeoutException()).when(firstChannel).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);

        assertThrows(TimeoutException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{1}));

        returnCallbacks.get(0).handle(new Return(312, "NO_ROUTE", "exchange-1", "rk-1",
                new AMQP.BasicProperties(), new byte[0]));

        publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{2});

        assertEquals(2, channels.size());
        assertEquals(2, published.size());
        assertEquals("5:true", published.get(1).style);
    }

    @Test
    void confirmSelectFailureClosesNewChannel() throws Exception {
        doAnswer(invocation -> {
            final Channel channel = newPublishingChannel();
            doThrow(new IOException("select failed")).when(channel).confirmSelect();
            return channel;
        }).when(connection).newChannel();

        final PublishingChannel publishingChannel = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        final IOException error = assertThrows(IOException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{1}));

        assertEquals("select failed", error.getMessage());
        verify(channels.get(0)).close();
        assertTrue(published.isEmpty());
    }

    @Test
    void channelCloseFailureIsSuppressedOnConfirmFailure() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        publishingChannel.start();
        final Channel channel = prepareChannel();
        final IOException confirmFailure = new IOException("confirm failed");
        final IOException closeFailure = new IOException("close failed");
        doThrow(confirmFailure).when(channel).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
        doThrow(closeFailure).when(channel).close();

        final IOException error = assertThrows(IOException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> new byte[]{1}));

        assertSame(confirmFailure, error);
        assertEquals(1, error.getSuppressed().length);
        assertSame(closeFailure, error.getSuppressed()[0]);
    }

    @Test
    void checkedExceptionsInsideObserverExitAsOriginalTypes() throws Exception {
        final PublishingChannel publishingChannel = new PublishingChannel(connection);
        publishingChannel.start();

        final IOException ioFailure = new IOException("boom");
        assertSame(ioFailure, assertThrows(IOException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> {
                            throw ioFailure;
                        })));

        assertThrows(InterruptedException.class,
                () -> publishingChannel.publish("queue-1", "exchange-1", "rk-1",
                        new AMQP.BasicProperties(), properties -> properties, () -> {
                            throw new InterruptedException();
                        }));
        assertTrue(Thread.interrupted());

        assertEquals(2, observer.failures.size());
        assertTrue(observer.failures.stream().anyMatch(failure -> failure.getCause() instanceof IOException));
        assertTrue(observer.failures.stream().anyMatch(failure -> failure.getCause() instanceof InterruptedException));
        assertTrue(published.isEmpty());
    }

    @Test
    void nonPositiveConfirmTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PublishingChannel(connection, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PublishingChannel(connection, -1L));
    }

    @Test
    void observerPostProcessingCannotTurnConfirmedHandoffIntoFailure() throws Exception {
        var postFailure = new RMQObserver(null) {
            @Override
            public <T> T executePublish(PublishObserverContext context,
                                        Function<PublishMessageDetails, T> publish) {
                proceedPublish(context, publish);
                throw new IllegalStateException("post-processing failed");
            }

            @Override
            public <T> T executeConsume(ConsumeObserverContext context,
                                        Function<ConsumeMessageDetails, T> consume) {
                return proceedConsume(context, consume);
            }
        };
        when(connection.getRootObserver()).thenReturn(postFailure);
        var transport = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        transport.publish("retry-queue", "exchange", "routing-key", new AMQP.BasicProperties(), new byte[]{1});
        assertEquals(1, published.size());
        verify(channels.get(0)).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
        verify(channels.get(0), never()).close();
    }

    @Test
    void swallowingObserverCannotAcknowledgeFailedConfirmedHandoff() throws Exception {
        var swallowing = new RMQObserver(null) {
            @Override
            public <T> T executePublish(PublishObserverContext context,
                                        Function<PublishMessageDetails, T> publish) {
                return null;
            }

            @Override
            public <T> T executeConsume(ConsumeObserverContext context,
                                        Function<ConsumeMessageDetails, T> consume) {
                return proceedConsume(context, consume);
            }
        };
        when(connection.getRootObserver()).thenReturn(swallowing);
        var transport = new PublishingChannel(connection, CONFIRM_TIMEOUT_MS);
        assertThrows(IOException.class, () -> transport.publish("retry-queue", "exchange", "routing-key",
                new AMQP.BasicProperties(), new byte[]{1}));
        assertTrue(published.isEmpty());
    }

    private Channel prepareChannel() throws IOException {
        var prepared = newPublishingChannel();
        doAnswer(invocation -> prepared).doAnswer(invocation -> newPublishingChannel())
                .when(connection).newChannel();
        return prepared;
    }

    private Channel newPublishingChannel() throws IOException {
        final Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            published.add(new Published("4",
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    null,
                    invocation.getArgument(2),
                    invocation.getArgument(3)));
            return null;
        }).when(channel).basicPublish(anyString(), anyString(), any(AMQP.BasicProperties.class), any(byte[].class));
        doAnswer(invocation -> {
            published.add(new Published("5:" + invocation.getArgument(2),
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    (Boolean) invocation.getArgument(2),
                    invocation.getArgument(3),
                    invocation.getArgument(4)));
            return null;
        }).when(channel).basicPublish(anyString(), anyString(), anyBoolean(),
                any(AMQP.BasicProperties.class), any(byte[].class));
        doAnswer(invocation -> {
            returnCallbacks.add(invocation.getArgument(0));
            return null;
        }).when(channel).addReturnListener(any(ReturnCallback.class));
        channels.add(channel);
        return channel;
    }

    private static final class RecordingObserver extends RMQObserver {
        private final List<PublishObserverContext> contexts = new ArrayList<>();
        private final List<RuntimeException> failures = new ArrayList<>();

        private RecordingObserver() {
            super(null);
        }

        @Override
        public <T> T executePublish(final PublishObserverContext context,
                                    final Function<PublishMessageDetails, T> publishFunction) {
            contexts.add(context);
            INSIDE_OBSERVER.set(Boolean.TRUE);
            try {
                return proceedPublish(context, publishFunction);
            } catch (RuntimeException error) {
                failures.add(error);
                throw error;
            } finally {
                INSIDE_OBSERVER.set(Boolean.FALSE);
            }
        }

        @Override
        public <T> T executeConsume(final ConsumeObserverContext context,
                                    final Function<ConsumeMessageDetails, T> consumeFunction) {
            return proceedConsume(context, consumeFunction);
        }
    }

    private static final class Published {
        private final String style;
        private final String exchange;
        private final String routingKey;
        private final Boolean mandatory;
        private final AMQP.BasicProperties properties;
        private final byte[] body;

        private Published(final String style,
                          final String exchange,
                          final String routingKey,
                          final Boolean mandatory,
                          final AMQP.BasicProperties properties,
                          final byte[] body) {
            this.style = style;
            this.exchange = exchange;
            this.routingKey = routingKey;
            this.mandatory = mandatory;
            this.properties = properties;
            this.body = body;
        }
    }
}
