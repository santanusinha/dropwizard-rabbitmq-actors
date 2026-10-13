package io.appform.dropwizard.actors.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.observers.ConsumeMessageDetails;
import io.appform.dropwizard.actors.observers.ConsumeObserverContext;
import io.appform.dropwizard.actors.observers.PublishMessageDetails;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnmanagedPublisherTransportTest {

    private static final String QUEUE_NAME = "test-queue";
    private static final String EXCHANGE = "test-exchange";

    private static final ThreadLocal<Boolean> INSIDE_OBSERVER = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final ObjectMapper mapper = new ObjectMapper();

    private RMQConnection connection;
    private ActorConfig config;
    private final List<Channel> channels = new ArrayList<>();
    private final List<Published> published = new ArrayList<>();
    private final List<Boolean> dispatchInsideObserverScope = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        connection = mock(RMQConnection.class);
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.channel()).thenReturn(mock(Channel.class));
        when(connection.newChannel()).thenAnswer(invocation -> newPublishingChannel());
        config = ActorConfig.builder()
                .exchange(EXCHANGE)
                .build();
        channels.clear();
        published.clear();
        dispatchInsideObserverScope.clear();
    }

    @Test
    void publishUsesFourArgBasicPublishWithoutConfirms() throws Exception {
        final UnmanagedPublisher<String> publisher = newPublisher();
        publisher.start();

        publisher.publish("hello");

        assertEquals(1, published.size());
        final Published record = published.get(0);
        assertEquals(EXCHANGE, record.exchange);
        assertEquals(QUEUE_NAME, record.routingKey);
        verify(channels.get(0)).basicPublish(anyString(), anyString(), any(AMQP.BasicProperties.class), any(byte[].class));
        assertArrayEquals(mapper.writeValueAsBytes("hello"), record.body);
        assertFalse(dispatchInsideObserverScope.isEmpty());
    }

    @Test
    void publishEnrichesPropertiesWithPublishedTimestamp() throws Exception {
        final UnmanagedPublisher<String> publisher = newPublisher();
        publisher.start();

        publisher.publish("hello");

        assertEquals(1, published.size());
        final AMQP.BasicProperties properties = published.get(0).properties;
        assertNotNull(properties);
        assertNotNull(properties.getHeaders());
        final Object timestamp = properties.getHeaders().get(Constants.MESSAGE_PUBLISHED_TEXT);
        assertNotNull(timestamp);
        assertTrue(((Number) timestamp).longValue() > 0);
        assertEquals(Integer.valueOf(2), properties.getDeliveryMode());
    }

    @Test
    void publishPreservesRawHeadersThroughTransform() throws Exception {
        final UnmanagedPublisher<String> publisher = newPublisher();
        publisher.start();

        final AMQP.BasicProperties rawProperties = new AMQP.BasicProperties.Builder()
                .headers(Map.of("x-custom", "kept"))
                .build();
        publisher.publish("hello", rawProperties);

        assertEquals(1, published.size());
        final Map<String, Object> headers = published.get(0).properties.getHeaders();
        assertEquals("kept", headers.get("x-custom"));
        assertNotNull(headers.get(Constants.MESSAGE_PUBLISHED_TEXT));
    }

    @Test
    void publishRunsSerializationInsideObserverScope() throws Exception {
        final RecordingObserver observer = new RecordingObserver();
        when(connection.getRootObserver()).thenReturn(observer);
        final UnmanagedPublisher<String> publisher = newPublisher();
        publisher.start();

        publisher.publish("hello");

        assertEquals(1, observer.contexts.size());
        final PublishObserverContext context = observer.contexts.get(0);
        assertEquals(QUEUE_NAME, context.getQueueName());
        assertNotNull(context.getMessageProperties());
        assertEquals(1, published.size());
        assertEquals(Boolean.TRUE, dispatchInsideObserverScope.get(0));
    }

    @Test
    void observerSeesFailureWhenPublishFails() throws Exception {
        final RecordingObserver observer = new RecordingObserver();
        when(connection.getRootObserver()).thenReturn(observer);
        final UnmanagedPublisher<String> publisher = newPublisher();
        publisher.start();
        doThrow(new IOException("broken pipe")).when(channels.get(0))
                .basicPublish(anyString(), anyString(), any(AMQP.BasicProperties.class), any(byte[].class));

        final var error = assertThrows(io.appform.dropwizard.actors.common.RabbitmqActorException.class,
                () -> publisher.publish("hello"));

        assertEquals("broken pipe", error.getCause().getMessage());
        assertEquals(1, observer.contexts.size());
        assertFalse(observer.failures.isEmpty());
        assertEquals("broken pipe", observer.failures.get(0).getCause().getMessage());
    }

    @Test
    void noPublishAfterStopUntilRestart() throws Exception {
        final UnmanagedPublisher<String> publisher = newPublisher();
        publisher.start();
        publisher.publish("first");

        publisher.stop();

        assertThrows(Exception.class, () -> publisher.publish("second"));

        publisher.start();
        publisher.publish("third");

        assertEquals(2, published.size());
        assertEquals(2, channels.size());
        verify(channels.get(0)).close();
    }

    @Test
    void serializationFailurePreservesExistingActorExceptionWrapping() throws Exception {
        final ObjectMapper failingMapper = mock(ObjectMapper.class);
        final JsonProcessingException serializationFailure = new JsonProcessingException("cannot serialize") {
        };
        when(failingMapper.writeValueAsBytes(any())).thenThrow(serializationFailure);
        final UnmanagedPublisher<String> publisher =
                new UnmanagedPublisher<>(QUEUE_NAME, config, connection, failingMapper);
        publisher.start();

        final var error = assertThrows(io.appform.dropwizard.actors.common.RabbitmqActorException.class,
                () -> publisher.publish("hello"));

        assertSame(serializationFailure, error.getCause());
        assertTrue(published.isEmpty());
    }

    @Test
    void publishWithDelayTtlUsesTtlExchange() throws Exception {
        config = ActorConfig.builder()
                .exchange(EXCHANGE)
                .delayType(DelayType.TTL)
                .build();
        final RecordingObserver observer = new RecordingObserver();
        when(connection.getRootObserver()).thenReturn(observer);
        final UnmanagedPublisher<String> publisher = newPublisher();
        publisher.start();

        publisher.publishWithDelay("hello", 5000L);

        assertEquals(1, published.size());
        final Published record = published.get(0);
        assertEquals(EXCHANGE + "_TTL", record.exchange);
        assertEquals(QUEUE_NAME, record.routingKey);
        assertEquals("5000", record.properties.getExpiration());
        assertArrayEquals(mapper.writeValueAsBytes("hello"), record.body);
        assertEquals(1, observer.contexts.size());
        assertEquals(Boolean.TRUE, dispatchInsideObserverScope.get(0));
    }

    @Test
    void publishWithExpiryAddsExpiryHeader() throws Exception {
        final UnmanagedPublisher<String> publisher = newPublisher();
        publisher.start();

        publisher.publishWithExpiry("hello", 60000L);

        assertEquals(1, published.size());
        final Map<String, Object> headers = published.get(0).properties.getHeaders();
        assertNotNull(headers);
        assertNotNull(headers.get(Constants.MESSAGE_EXPIRY_TEXT));
        assertTrue(((Number) headers.get(Constants.MESSAGE_EXPIRY_TEXT)).longValue() > System.currentTimeMillis());
    }

    @Test
    void pendingMessagesCountReadsChannel() throws Exception {
        final UnmanagedPublisher<String> publisher = newPublisher();
        publisher.start();
        when(channels.get(0).messageCount(QUEUE_NAME)).thenReturn(7L);

        assertEquals(7L, publisher.pendingMessagesCount());
    }

    @Test
    void pendingMessagesCountReturnsMaxOnClosedChannel() {
        final UnmanagedPublisher<String> publisher = newPublisher();
        assertEquals(Long.MAX_VALUE, publisher.pendingMessagesCount());
    }

    private UnmanagedPublisher<String> newPublisher() {
        return new UnmanagedPublisher<>(QUEUE_NAME, config, connection, mapper);
    }

    private Channel newPublishingChannel() throws IOException {
        final Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            dispatchInsideObserverScope.add(INSIDE_OBSERVER.get());
            published.add(new Published(invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    invocation.getArgument(3)));
            return null;
        }).when(channel).basicPublish(anyString(), anyString(), any(AMQP.BasicProperties.class), any(byte[].class));
        channels.add(channel);
        return channel;
    }

    private final class RecordingObserver extends RMQObserver {
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
                return publishFunction.apply(PublishMessageDetails.builder()
                        .messageProperties(context.getMessageProperties())
                        .build());
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
        private final String exchange;
        private final String routingKey;
        private final AMQP.BasicProperties properties;
        private final byte[] body;

        private Published(final String exchange,
                          final String routingKey,
                          final AMQP.BasicProperties properties,
                          final byte[] body) {
            this.exchange = exchange;
            this.routingKey = routingKey;
            this.properties = properties;
            this.body = body;
        }
    }
}
