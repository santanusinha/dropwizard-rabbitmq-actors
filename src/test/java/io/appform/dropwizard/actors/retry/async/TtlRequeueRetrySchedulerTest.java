package io.appform.dropwizard.actors.retry.async;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ReturnCallback;
import com.rabbitmq.client.impl.LongStringHelper;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.QueueType;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import io.appform.dropwizard.actors.observers.PublishMessageDetails;
import io.appform.dropwizard.actors.common.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TtlRequeueRetrySchedulerTest {

    private static final String QUEUE = "queue_0";
    private static final String OTHER_QUEUE = "other-queue";
    private static final long STARTED_AT = 900L;
    private static final long NOW = 1_000L;

    private final RMQConnection connection = mock(RMQConnection.class);
    private final Channel topology = mock(Channel.class);
    private final Channel publisher = mock(Channel.class);
    private final byte[] body = new byte[]{1, 2};
    private final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
            .messageId("message").deliveryMode(2).expiration("20")
            .headers(Map.of("custom", "value")).build();
    private TtlRequeueRetryScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.newChannel()).thenReturn(topology, publisher);
        when(publisher.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            var topologyChannel = invocation.<Channel>getArgument(0);
            topologyChannel.queueDeclare(invocation.getArgument(1), true, false, false, invocation.getArgument(4));
            topologyChannel.queueBind(invocation.getArgument(1), invocation.getArgument(3), invocation.getArgument(2));
            return null;
        }).when(connection).ensure(any(Channel.class), anyString(), anyString(), anyString(), any());
        doAnswer(invocation -> {
            invocation.<Channel>getArgument(0)
                    .queueBind(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
            return null;
        }).when(connection).addBinding(any(Channel.class), anyString(), anyString(), anyString());
        scheduler = new TtlRequeueRetryScheduler(new TtlRequeueRetryScheduler.Context(connection,
                ActorConfig.builder().exchange("exchange").queueType(QueueType.QUORUM)
                        .priorityQueue(true).build(),
                "exchange", "queue", Set.of("queue_0", "queue_1")), 5000);
    }

    @Test
    void observesRetryDestinationAndPreservesOriginalAgeAndExpiry() throws Exception {
        var observer = mock(RMQObserver.class);
        doAnswer(invocation -> {
            var context = invocation.<PublishObserverContext>getArgument(0);
            var publish = invocation.<java.util.function.Function<PublishMessageDetails, Object>>getArgument(1);
            return publish.apply(PublishMessageDetails.builder()
                    .messageProperties(context.getMessageProperties()).build());
        }).when(observer).executePublish(any(), any());
        when(connection.getRootObserver()).thenReturn(observer);
        scheduler = new TtlRequeueRetryScheduler(new TtlRequeueRetryScheduler.Context(connection,
                new ActorConfig(), "exchange", "queue", Set.of(QUEUE)), 1234);
        var original = properties(Map.of(Constants.MESSAGE_PUBLISHED_TEXT, 800L,
                Constants.MESSAGE_EXPIRY_TEXT, 2000L));
        when(connection.newChannel()).thenReturn(topology, publisher, topology, publisher);
        scheduler.start();
        scheduler.schedule(QUEUE, body, original, new RetryState(2, STARTED_AT), 100);

        var context = ArgumentCaptor.forClass(PublishObserverContext.class);
        verify(observer).executePublish(context.capture(), any());
        assertEquals("queue_RETRY_100", context.getValue().getQueueName());
        var published = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(publisher).basicPublish(eq("exchange_RETRY_100"), eq(QUEUE), eq(true),
                published.capture(), same(body));
        assertEquals(800L, published.getValue().getHeaders().get(Constants.MESSAGE_PUBLISHED_TEXT));
        assertEquals(2000L, published.getValue().getHeaders().get(Constants.MESSAGE_EXPIRY_TEXT));
        verify(publisher).waitForConfirmsOrDie(1234);
        scheduler.stop();
        scheduler.start();
        scheduler.schedule(QUEUE, body, original, new RetryState(3, STARTED_AT), 100);
        verify(connection, times(4)).newChannel();
        verify(topology, times(2)).exchangeDeclare("exchange_RETRY_100", "direct", true);
    }

    private AMQP.BasicProperties properties(Map<String, Object> headers) {
        return new AMQP.BasicProperties.Builder().headers(headers).build();
    }

    private Map<String, Object> retryHeaders(String queue, Object attempt, Object startedAt) {
        var headers = new LinkedHashMap<String, Object>();
        headers.put(TtlRequeueRetryScheduler.RETRY_QUEUE_HEADER, queue);
        if (attempt != null) {
            headers.put(TtlRequeueRetryScheduler.RETRY_ATTEMPT_HEADER, attempt);
        }
        if (startedAt != null) {
            headers.put(TtlRequeueRetryScheduler.RETRY_STARTED_AT_HEADER, startedAt);
        }
        return headers;
    }

    @Test
    void declaresFixedTtlQueuesWithOriginalShardBindings() throws Exception {
        scheduler.start();

        for (long delay : Set.of(100L, 1000L)) {
            when(connection.newChannel()).thenReturn(topology, publisher, topology);
            scheduler.schedule("queue_0", body, properties,
                    new RetryState(2, STARTED_AT), delay);
            verify(topology).exchangeDeclare("exchange_RETRY_" + delay, "direct", true);
            verify(topology).queueDeclare("queue_RETRY_" + delay, true, false, false,
                    Map.of("x-message-ttl", delay, "x-dead-letter-exchange", "exchange",
                            "x-queue-type", "quorum", "x-quorum-initial-group-size", 3));
            verify(topology).queueBind("queue_RETRY_" + delay, "exchange_RETRY_" + delay, "queue_0");
            verify(topology).queueBind("queue_RETRY_" + delay, "exchange_RETRY_" + delay, "queue_1");
        }
        verify(topology, times(2)).close();
    }

    @Test
    void confirmsMandatoryPublishMergesRetryHeadersAndPreservesPayloadAndProperties() throws Exception {
        scheduler.start();
        scheduler.schedule("queue_1", body, properties, new RetryState(4, STARTED_AT), 100);

        var captured = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(publisher).confirmSelect();
        verify(publisher).basicPublish(eq("exchange_RETRY_100"), eq("queue_1"), eq(true),
                captured.capture(), same(body));
        verify(publisher).waitForConfirmsOrDie(5000);
        var published = captured.getValue();
        assertEquals(Map.of(TtlRequeueRetryScheduler.RETRY_QUEUE_HEADER, "queue_1",
                TtlRequeueRetryScheduler.RETRY_ATTEMPT_HEADER, 4L,
                TtlRequeueRetryScheduler.RETRY_STARTED_AT_HEADER, STARTED_AT,
                "custom", "value"), published.getHeaders());
        assertEquals("message", published.getMessageId());
        assertEquals(2, published.getDeliveryMode());
        assertNull(published.getExpiration());
    }

    @Test
    void scheduleMergesRetryHeadersWithoutMutatingOriginalProperties() throws Exception {
        scheduler.start();
        var originalHeaders = new HashMap<String, Object>(Map.of("custom", "value"));
        var originalProperties = properties(originalHeaders);

        scheduler.schedule("queue_0", body, originalProperties, new RetryState(2, STARTED_AT), 100);

        assertEquals(Map.of("custom", "value"), originalHeaders);
        var captured = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(publisher).basicPublish(anyString(), anyString(), anyBoolean(), captured.capture(), same(body));
        assertNotSame(originalProperties, captured.getValue());
        assertEquals(Map.of(TtlRequeueRetryScheduler.RETRY_QUEUE_HEADER, "queue_0",
                TtlRequeueRetryScheduler.RETRY_ATTEMPT_HEADER, 2L,
                TtlRequeueRetryScheduler.RETRY_STARTED_AT_HEADER, STARTED_AT,
                "custom", "value"), captured.getValue().getHeaders());
    }

    @Test
    void restoredStateSurvivesASchedulingRoundTrip() throws Exception {
        scheduler.start();
        scheduler.schedule(QUEUE, body, properties(Map.of("custom", "value")),
                new RetryState(3, STARTED_AT), 100);

        var captured = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(publisher).basicPublish(anyString(), anyString(), anyBoolean(), captured.capture(), same(body));
        var restored = scheduler.restoreState(QUEUE, captured.getValue(), NOW);

        assertEquals(new RetryState(3, STARTED_AT), restored);
    }

    @Test
    void declaresExactRuntimeDelayAndRejectsInvalidRequests() throws Exception {
        scheduler.start();

        scheduler.schedule("queue_0", body, properties, new RetryState(2, STARTED_AT), 5000);
        verify(publisher).basicPublish(eq("exchange_RETRY_5000"), eq("queue_0"), eq(true), any(), same(body));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule("queue_0", body, properties, new RetryState(2, STARTED_AT), -1));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule("another-queue", body, properties, new RetryState(2, STARTED_AT), 100));
    }

    @Test
    void returnedMessagesFailAndDiscardChannel() throws Exception {
        var callback = new AtomicReference<ReturnCallback>();
        when(publisher.addReturnListener(any(ReturnCallback.class))).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return null;
        });
        doAnswer(invocation -> {
            callback.get().handle(null);
            return null;
        }).when(publisher).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());
        scheduler.start();

        assertThrows(IOException.class,
                () -> scheduler.schedule("queue_0", body, properties, new RetryState(2, STARTED_AT), 100));
        verify(publisher).close();
    }

    @Test
    void confirmTimeoutDiscardsChannelAndNextHandoffOpensAnother() throws Exception {
        doThrow(new TimeoutException()).when(publisher).waitForConfirmsOrDie(5000);
        scheduler.start();
        assertThrows(TimeoutException.class,
                () -> scheduler.schedule("queue_0", body, properties, new RetryState(2, STARTED_AT), 100));
        verify(publisher).close();
        var next = mock(Channel.class);
        when(next.isOpen()).thenReturn(true);
        when(connection.newChannel()).thenReturn(next);

        scheduler.schedule("queue_0", body, properties, new RetryState(2, STARTED_AT), 100);

        verify(next).confirmSelect();
        verify(next).waitForConfirmsOrDie(5000);
    }

    @Test
    void boundsRuntimeDelayLevelsWithoutShorteningRequests() throws Exception {
        scheduler = new TtlRequeueRetryScheduler(new TtlRequeueRetryScheduler.Context(connection,
                ActorConfig.builder().exchange("exchange").build(),
                "exchange", "queue", Set.of("queue_0")), 5000, 1);
        scheduler.start();
        scheduler.schedule("queue_0", body, properties, new RetryState(2, STARTED_AT), 100);

        assertThrows(IllegalStateException.class,
                () -> scheduler.schedule("queue_0", body, properties, new RetryState(3, STARTED_AT), 200));
        verify(publisher, times(1)).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());
    }

    @Test
    void reusesChannelAndRejectsSchedulingOutsideLifecycle() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> scheduler.schedule("queue_0", body, properties, new RetryState(2, STARTED_AT), 100));
        when(connection.newChannel()).thenReturn(topology, publisher, topology);
        scheduler.start();
        scheduler.schedule("queue_0", body, properties, new RetryState(2, STARTED_AT), 100);
        scheduler.schedule("queue_1", body, properties, new RetryState(2, STARTED_AT), 100);
        verify(connection, times(2)).newChannel();
        verify(topology, times(1)).queueDeclare(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), any());

        scheduler.stop();

        verify(publisher).close();
        assertThrows(IllegalStateException.class,
                () -> scheduler.schedule("queue_0", body, properties, new RetryState(2, STARTED_AT), 100));
    }

    @Test
    void freshDeliveryWithoutRetryHeadersStartsAtAttemptOne() {
        assertEquals(new RetryState(1, NOW), scheduler.restoreState(QUEUE, new AMQP.BasicProperties(), NOW));
        assertEquals(new RetryState(1, NOW), scheduler.restoreState(QUEUE, null, NOW));
        assertEquals(new RetryState(1, NOW),
                scheduler.restoreState(QUEUE, properties(Map.of("custom", "value")), NOW));
    }

    @Test
    void retryHeadersOnTheirQueueRestoreThePersistedState() {
        assertEquals(new RetryState(2, STARTED_AT),
                scheduler.restoreState(QUEUE, properties(retryHeaders(QUEUE, 2L, STARTED_AT)), NOW));
    }

    @Test
    void integerSubtypesInHeadersAreAccepted() {
        assertEquals(new RetryState(3, STARTED_AT),
                scheduler.restoreState(QUEUE, properties(retryHeaders(QUEUE, (byte) 3, (short) STARTED_AT)), NOW));
        assertEquals(new RetryState(3, STARTED_AT),
                scheduler.restoreState(QUEUE, properties(retryHeaders(QUEUE, 3, (int) STARTED_AT)), NOW));
    }

    @Test
    void amqpLongStringQueueHeaderIsAcceptedViaToString() {
        var headers = retryHeaders(QUEUE, 2L, STARTED_AT);
        headers.put(TtlRequeueRetryScheduler.RETRY_QUEUE_HEADER,
                LongStringHelper.asLongString(QUEUE));
        assertEquals(new RetryState(2, STARTED_AT), scheduler.restoreState(QUEUE, properties(headers), NOW));
    }

    @Test
    void retryHeadersScopedToAnotherQueueRestartAtAttemptOne() {
        var headers = retryHeaders(OTHER_QUEUE, 7L, 123L);
        assertEquals(new RetryState(1, NOW), scheduler.restoreState(QUEUE, properties(headers), NOW));
    }

    @Test
    void malformedRetryMetadataFailsRestoreState() {
        var cases = new LinkedHashMap<String, Map<String, Object>>();
        cases.put("zero attempt count", retryHeaders(QUEUE, 0L, 900L));
        cases.put("negative attempt count", retryHeaders(QUEUE, -3L, 900L));
        cases.put("attempt count at int limit", retryHeaders(QUEUE, Integer.MAX_VALUE, 900L));
        cases.put("non numeric attempt count", retryHeaders(QUEUE, "two", 900L));
        cases.put("fractional attempt count", retryHeaders(QUEUE, 2.5d, 900L));
        cases.put("float attempt count", retryHeaders(QUEUE, 2.0f, 900L));
        cases.put("overflowing attempt count type", retryHeaders(QUEUE, java.math.BigInteger.valueOf(2), 900L));
        cases.put("negative started at", retryHeaders(QUEUE, 2L, -1L));
        cases.put("future started at", retryHeaders(QUEUE, 2L, NOW + 1));
        cases.put("non numeric started at", retryHeaders(QUEUE, 2L, "yesterday"));
        cases.put("fractional started at", retryHeaders(QUEUE, 2L, 900.5d));
        cases.put("overflowing started at type", retryHeaders(QUEUE, 2L, java.math.BigInteger.valueOf(900)));
        cases.put("attempt count without queue scoping",
                Map.of(TtlRequeueRetryScheduler.RETRY_ATTEMPT_HEADER, 2L,
                        TtlRequeueRetryScheduler.RETRY_STARTED_AT_HEADER, 900L));
        cases.put("started at without queue scoping",
                Map.of(TtlRequeueRetryScheduler.RETRY_STARTED_AT_HEADER, 900L));

        cases.forEach((name, headers) -> {
            var error = assertThrows(IllegalStateException.class,
                    () -> scheduler.restoreState(QUEUE, properties(headers), NOW), name);
            assertTrue(error.getMessage().contains("Malformed actor retry metadata"), name);
        });
    }
}
