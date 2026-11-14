package io.appform.dropwizard.actors.retry.async;

import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ReturnCallback;
import com.rabbitmq.client.impl.LongStringHelper;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.QueueType;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.PublishingChannel;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.ActorRetryerBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TtlRequeueRetrySchedulerTest {
    private final RMQConnection connection = mock(RMQConnection.class);
    private final Channel topology = mock(Channel.class);
    private final Channel publisher = mock(Channel.class);
    private final byte[] body = {1, 2};
    private TtlRequeueRetryScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.newChannel()).thenReturn(topology, publisher);
        when(publisher.isOpen()).thenReturn(true);
        scheduler = scheduler("exchange", QueueType.QUORUM);
    }

    private TtlRequeueRetryScheduler scheduler(String exchange, QueueType type) {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .withWaitStrategy(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS)).build();
        return new TtlRequeueRetryScheduler(connection, policy,
                ActorConfig.builder().exchange(exchange).queueType(type).build(), exchange, "", null,
                new PublishingChannel(connection, 5000));
    }

    private String waiting() {
        return NamingUtils.prefixWithNamespace("exchange_RETRY_quorum_100");
    }

    private AMQP.BasicProperties properties(Map<String, Object> headers) {
        return new AMQP.BasicProperties.Builder().headers(headers).build();
    }

    private Map<String, Object> headers(Object queue, Object attempt, Object startedAt) {
        return Map.of(TtlRequeueRetryScheduler.RETRY_QUEUE_HEADER, queue,
                TtlRequeueRetryScheduler.RETRY_ATTEMPT_HEADER, attempt,
                TtlRequeueRetryScheduler.RETRY_STARTED_AT_HEADER, startedAt);
    }

    @Test
    void startDeclaresDurableWaitingTopologyOnce() throws Exception {
        scheduler.start();
        scheduler.start();
        verify(topology).exchangeDeclare(waiting(), "fanout", true);
        verify(connection).ensure(topology, waiting(), "", waiting(),
                Map.of("x-queue-type", "quorum", "x-message-ttl", 100L, "x-dead-letter-exchange", "exchange"));
        verify(topology).close();
        verify(connection).newChannel();
    }

    @Test
    void destinationExchangeAndQueueTypeSeparateWaitingQueues() {
        assertNotEquals(scheduler.waitingQueue(100), scheduler("other-exchange", QueueType.QUORUM).waitingQueue(100));
        assertNotEquals(scheduler.waitingQueue(100), scheduler("exchange", QueueType.CLASSIC).waitingQueue(100));
        assertNotEquals(scheduler.waitingQueue(100), scheduler.waitingQueue(200));
    }

    @Test
    void classicV2UsesValidClassicWireType() throws Exception {
        scheduler = scheduler("exchange", QueueType.CLASSIC_V2);
        scheduler.start();
        var waiting = NamingUtils.prefixWithNamespace("exchange_RETRY_classic_v2_100");
        verify(connection).ensure(topology, waiting, "", waiting,
                Map.of("x-queue-type", "classic", "x-message-ttl", 100L, "x-dead-letter-exchange", "exchange"));
    }

    @Test
    void confirmedPublishPreservesPayloadAndHeadersWithoutMutatingOriginal() throws Exception {
        scheduler.start();
        var originalHeaders = new HashMap<String, Object>(Map.of("custom", "value", "x-delay", 5000,
                "message-published-at", 800L));
        var original = new AMQP.BasicProperties.Builder().messageId("message").deliveryMode(2)
                .expiration("20").headers(originalHeaders).build();
        scheduler.schedule("queue_1", body, original, new RetryState(2, 900), 100);
        var captured = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(publisher).basicPublish(eq(waiting()), eq("queue_1"), eq(true), captured.capture(), same(body));
        verify(publisher).confirmSelect();
        verify(publisher).waitForConfirmsOrDie(5000);
        var published = captured.getValue();
        assertEquals("message", published.getMessageId());
        assertEquals(2, published.getDeliveryMode());
        assertNull(published.getExpiration());
        assertEquals("20", original.getExpiration());
        assertEquals(5000, originalHeaders.get("x-delay"));
        assertFalse(published.getHeaders().containsKey("x-delay"));
        assertEquals("value", published.getHeaders().get("custom"));
        assertEquals(800L, published.getHeaders().get("message-published-at"));
        assertEquals(new RetryState(2, 900), scheduler.restoreState("queue_1", published, 1000));
    }

    @Test
    void shardsUseSameWaitingExchangeWithIndependentRoutingAndState() throws Exception {
        scheduler.start();
        var original = new AMQP.BasicProperties();
        scheduler.schedule("queue_0", body, original, new RetryState(2, 900), 100);
        scheduler.schedule("queue_1", body, original, new RetryState(3, 950), 100);
        var first = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        var second = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(publisher).basicPublish(eq(waiting()), eq("queue_0"), eq(true), first.capture(), same(body));
        verify(publisher).basicPublish(eq(waiting()), eq("queue_1"), eq(true), second.capture(), same(body));
        assertEquals(new RetryState(2, 900), scheduler.restoreState("queue_0", first.getValue(), 1000));
        assertEquals(new RetryState(3, 950), scheduler.restoreState("queue_1", second.getValue(), 1000));
        verify(connection, times(2)).newChannel();
    }

    @Test
    void schedulingOutsideLifecycleFailsAndRestartRedeclares() throws Exception {
        assertThrows(RetrySchedulingException.class, () -> scheduler.schedule("queue", body,
                new AMQP.BasicProperties(), new RetryState(2, 900), 100));
        scheduler.start();
        scheduler.stop();
        assertThrows(RetrySchedulingException.class, () -> scheduler.schedule("queue", body,
                new AMQP.BasicProperties(), new RetryState(2, 900), 100));
        when(connection.newChannel()).thenReturn(topology);
        scheduler.start();
        verify(topology, times(2)).exchangeDeclare(waiting(), "fanout", true);
    }

    @Test
    void confirmTimeoutIsTransportFailureAndNextPublishOpensNewChannel() throws Exception {
        scheduler.start();
        var timeout = new TimeoutException("no confirm");
        doThrow(timeout).when(publisher).waitForConfirmsOrDie(5000);
        var error = assertThrows(RetrySchedulingException.class, () -> scheduler.schedule("queue", body,
                new AMQP.BasicProperties(), new RetryState(2, 900), 100));
        assertSame(timeout, error.getCause());
        verify(publisher).close();
        var next = mock(Channel.class);
        when(next.isOpen()).thenReturn(true);
        when(connection.newChannel()).thenReturn(next);
        scheduler.schedule("queue", body, new AMQP.BasicProperties(), new RetryState(2, 900), 100);
        verify(next).confirmSelect();
        verify(next).waitForConfirmsOrDie(5000);
    }

    @Test
    void unroutablePublishFailsHandoff() throws Exception {
        var callback = new AtomicReference<ReturnCallback>();
        when(publisher.addReturnListener(any(ReturnCallback.class))).thenAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return null;
        });
        doAnswer(invocation -> { callback.get().handle(null); return null; }).when(publisher)
                .basicPublish(anyString(), anyString(), anyBoolean(), any(), any());
        scheduler.start();
        var error = assertThrows(RetrySchedulingException.class, () -> scheduler.schedule("queue", body,
                new AMQP.BasicProperties(), new RetryState(2, 900), 100));
        assertTrue(error.getCause().getMessage().contains("unroutable"));
        verify(publisher).close();
    }

    @Test
    void freshDeliveryAndDifferentQueueStartAtAttemptOne() {
        assertEquals(new RetryState(1, 1000), scheduler.restoreState("queue", null, 1000));
        assertEquals(new RetryState(1, 1000), scheduler.restoreState("queue", new AMQP.BasicProperties(), 1000));
        assertEquals(new RetryState(1, 1000), scheduler.restoreState("queue",
                properties(headers("other", 3L, 900L)), 1000));
    }

    @Test
    void longStringQueueAndIntegerHeadersRestoreState() {
        assertEquals(new RetryState(3, 900), scheduler.restoreState("queue",
                properties(headers(LongStringHelper.asLongString("queue"), 3, 900)), 1000));
        assertEquals(new RetryState(3, 1500), scheduler.restoreState("queue",
                properties(headers("queue", 3L, 1500L)), 1000));
    }

    @Test
    void malformedMetadataDoesNotResetBudget() {
        for (var attempt : new Object[]{0L, -1L, Integer.MAX_VALUE, "two", 2.5d}) {
            assertThrows(IllegalStateException.class, () -> scheduler.restoreState("queue",
                    properties(headers("queue", attempt, 900L)), 1000));
        }
        assertThrows(IllegalStateException.class, () -> scheduler.restoreState("queue",
                properties(headers("queue", 2L, -1L)), 1000));
        assertThrows(IllegalStateException.class, () -> scheduler.restoreState("queue",
                properties(Map.of(TtlRequeueRetryScheduler.RETRY_ATTEMPT_HEADER, 2L)), 1000));
        assertThrows(IllegalStateException.class, () -> scheduler.restoreState("queue",
                properties(Map.of(TtlRequeueRetryScheduler.RETRY_QUEUE_HEADER, "queue")), 1000));
    }
}
