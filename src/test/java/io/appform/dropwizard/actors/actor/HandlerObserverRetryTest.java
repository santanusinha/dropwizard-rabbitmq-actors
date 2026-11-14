package io.appform.dropwizard.actors.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.base.Handler;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.exceptionhandler.config.DropConfig;
import io.appform.dropwizard.actors.exceptionhandler.config.SidelineConfig;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.exceptionhandler.handlers.MessageDropHandler;
import io.appform.dropwizard.actors.exceptionhandler.handlers.MessageSidelineHandler;
import io.appform.dropwizard.actors.observers.ConsumeMessageDetails;
import io.appform.dropwizard.actors.observers.ConsumeObserverContext;
import io.appform.dropwizard.actors.observers.PublishMessageDetails;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.WaitMode;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.async.AsyncRetryScheduler;
import io.appform.dropwizard.actors.retry.async.RetryState;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.dropwizard.util.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static io.appform.dropwizard.actors.common.Constants.MESSAGE_EXPIRY_TEXT;
import static io.appform.dropwizard.actors.common.Constants.MESSAGE_PUBLISHED_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HandlerObserverRetryTest {

    private static final String QUEUE = "observer.retry.queue";
    private static final String EXCHANGE = "observer.retry.exchange";
    private static final String CONSUMER_TAG = "observer.retry.ctag";
    private static final long DELIVERY_TAG = 42L;
    private static final long WAIT_MS = 250L;
    private static final String RETRY_QUEUE_HEADER = "x-actor-retry-queue";
    private static final String RETRY_ATTEMPT_HEADER = "x-actor-retry-attempt";
    private static final String RETRY_STARTED_AT_HEADER = "x-actor-retry-started-at";
    private static final String ENRICHED_HEADER = "x-test-enriched";

    public static class TestMessage {
        public String id = "m-1";
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Channel channel = mock(Channel.class);
    private final AsyncRetryScheduler scheduler = mock(AsyncRetryScheduler.class);

    @BeforeEach
    void setUp() {
        when(scheduler.restoreState(anyString(), any(), anyLong())).thenAnswer(invocation -> {
            var properties = invocation.getArgument(1, AMQP.BasicProperties.class);
            var headers = properties == null || properties.getHeaders() == null
                    ? Map.<String, Object>of() : properties.getHeaders();
            var attempt = headers.get(RETRY_ATTEMPT_HEADER);
            var startedAt = headers.get(RETRY_STARTED_AT_HEADER);
            if (attempt instanceof Number && startedAt instanceof Number) {
                return new RetryState(((Number) attempt).longValue(), ((Number) startedAt).longValue());
            }
            return new RetryState(1, (Long) invocation.getArgument(2));
        });
    }

    private Handler<TestMessage> handler(MessageHandlingFunction<TestMessage, Boolean> freshHandler,
                                        MessageHandlingFunction<TestMessage, Boolean> expiredHandler,
                                        RMQObserver observer) throws Exception {
        return handler(freshHandler, expiredHandler, observer, error -> false,
                new MessageSidelineHandler(new SidelineConfig()));
    }

    private Handler<TestMessage> handler(MessageHandlingFunction<TestMessage, Boolean> freshHandler,
                                        MessageHandlingFunction<TestMessage, Boolean> expiredHandler,
                                        RMQObserver observer,
                                        Function<Throwable, Boolean> errorCheck,
                                        ExceptionHandler exceptionHandler) throws Exception {
        var retryConfig = new CountLimitedFixedWaitRetryConfig();
        retryConfig.setWaitMode(WaitMode.ASYNC);
        retryConfig.setMaxAttempts(3);
        retryConfig.setWaitTime(Duration.milliseconds(WAIT_MS));
        var strategy = new RetryStrategyFactory().create(retryConfig);
        strategy.setActorRetryer(new io.appform.dropwizard.actors.retry.async.AsyncRetryer(
                strategy.getRetryPolicy(), scheduler));
        return new Handler<>(channel, mapper, TestMessage.class, 1,
                errorCheck, strategy, exceptionHandler, freshHandler, expiredHandler, observer, QUEUE);
    }

    private void deliver(Handler<TestMessage> target, boolean redelivered, Map<String, Object> headers,
                         long deliveryTag) throws IOException {
        target.handleDelivery(CONSUMER_TAG, new Envelope(deliveryTag, redelivered, EXCHANGE, QUEUE),
                new AMQP.BasicProperties().builder().headers(headers).build(),
                mapper.writeValueAsBytes(new TestMessage()));
    }

    private static Map<String, Object> retryHeaders(long attempt, long startedAt) {
        var headers = new HashMap<String, Object>();
        headers.put(RETRY_QUEUE_HEADER, QUEUE);
        headers.put(RETRY_ATTEMPT_HEADER, attempt);
        headers.put(RETRY_STARTED_AT_HEADER, startedAt);
        return headers;
    }

    private static final class RecordingObserver extends RMQObserver {
        private final List<ConsumeObserverContext> contexts = new CopyOnWriteArrayList<>();
        private final List<Throwable> failures = new CopyOnWriteArrayList<>();
        private final AtomicInteger successes = new AtomicInteger();

        private RecordingObserver(RMQObserver next) {
            super(next);
        }

        @Override
        public <T> T executePublish(PublishObserverContext context, Function<PublishMessageDetails, T> publishFunction) {
            return proceedPublish(context, publishFunction);
        }

        @Override
        public <T> T executeConsume(ConsumeObserverContext context, Function<ConsumeMessageDetails, T> consumeFunction) {
            contexts.add(context);
            try {
                var result = proceedConsume(context, consumeFunction);
                successes.incrementAndGet();
                return result;
            } catch (Throwable t) {
                failures.add(t);
                throw t;
            }
        }
    }

    private static final class EnrichingObserver extends RMQObserver {
        private EnrichingObserver(RMQObserver next) {
            super(next);
        }

        @Override
        public <T> T executePublish(PublishObserverContext context, Function<PublishMessageDetails, T> publishFunction) {
            return proceedPublish(context, publishFunction);
        }

        @Override
        public <T> T executeConsume(ConsumeObserverContext context, Function<ConsumeMessageDetails, T> consumeFunction) {
            var original = context.getMessageMetadata();
            var headers = new HashMap<>(original.getHeaders());
            headers.put(ENRICHED_HEADER, "true");
            return consumeFunction.apply(ConsumeMessageDetails.builder()
                    .messageMetadata(new MessageMetadata(original.isRedelivered(), original.getDelayInMs(), headers))
                    .build());
        }
    }

    @Test
    void businessFailureWithSuccessfulHandoffObservesFailedAttemptButAcksOriginalDelivery() throws Exception {
        var recorder = new RecordingObserver(new TerminalRMQObserver());
        var handler = handler((message, metadata) -> {
            throw new IOException("boom");
        }, (message, metadata) -> true, recorder);

        deliver(handler, false, Map.of(), DELIVERY_TAG);

        assertEquals(1, recorder.contexts.size());
        assertEquals(QUEUE, recorder.contexts.get(0).getQueueName());
        assertFalse(recorder.contexts.get(0).isRedelivered());
        assertEquals(1, recorder.failures.size());
        assertInstanceOf(RabbitmqActorException.class, recorder.failures.get(0));
        assertInstanceOf(IOException.class, recorder.failures.get(0).getCause());
        assertEquals(0, recorder.successes.get());

        var nextState = ArgumentCaptor.forClass(RetryState.class);
        verify(scheduler).schedule(eq(QUEUE), any(byte[].class), any(), nextState.capture(), eq(WAIT_MS));
        assertEquals(2L, nextState.getValue().attemptNumber());
        assertTrue(nextState.getValue().startedAt() > 0
                        && nextState.getValue().startedAt() <= System.currentTimeMillis(),
                "startedAt must be the epoch baseline for the first attempt");

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(channel, never()).abort();
    }

    @Test
    void retryRedeliveryOnFreshEnvelopeKeepsRedeliveredFalseAndPreservesHeaderTimestamps() throws Exception {
        var recorder = new RecordingObserver(new TerminalRMQObserver());
        var seenMetadata = new AtomicReference<MessageMetadata>();
        var handler = handler((message, metadata) -> {
            seenMetadata.set(metadata);
            return true;
        }, (message, metadata) -> {
            fail("A non-expired message must not reach the expired handler");
            return true;
        }, recorder);

        var now = System.currentTimeMillis();
        var headers = retryHeaders(2L, now - 60_000L);
        headers.put(MESSAGE_PUBLISHED_TEXT, now - 1_500L);
        headers.put(MESSAGE_EXPIRY_TEXT, now + 60_000L);

        deliver(handler, false, headers, DELIVERY_TAG);

        var context = recorder.contexts.get(0);
        assertFalse(context.isRedelivered());
        var metadata = seenMetadata.get();
        assertFalse(metadata.isRedelivered());
        assertEquals(2L, metadata.getHeaders().get(RETRY_ATTEMPT_HEADER));
        assertEquals(now - 1_500L, metadata.getHeaders().get(MESSAGE_PUBLISHED_TEXT));
        assertEquals(now + 60_000L, metadata.getHeaders().get(MESSAGE_EXPIRY_TEXT));
        assertTrue(metadata.getDelayInMs() >= 1_500L && metadata.getDelayInMs() <= 6_500L,
                "delayInMs must reflect the original publish age: " + metadata.getDelayInMs());
        assertEquals(1, recorder.successes.get());
        assertEquals(0, recorder.failures.size());
        verify(scheduler, never()).schedule(anyString(), any(), any(), any(), anyLong());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    void brokerRedeliveryWithRetryHeadersReportsRedeliveredAndAdvancesAttempt() throws Exception {
        var recorder = new RecordingObserver(new TerminalRMQObserver());
        var startedAt = System.currentTimeMillis() - 60_000L;
        var handler = handler((message, metadata) -> {
            throw new IOException("still failing");
        }, (message, metadata) -> true, recorder);

        deliver(handler, true, retryHeaders(2L, startedAt), DELIVERY_TAG);

        assertTrue(recorder.contexts.get(0).isRedelivered());
        assertEquals(1, recorder.failures.size());
        var nextState = ArgumentCaptor.forClass(RetryState.class);
        verify(scheduler).schedule(eq(QUEUE), any(byte[].class), any(), nextState.capture(), eq(WAIT_MS));
        assertEquals(3L, nextState.getValue().attemptNumber());
        assertEquals(startedAt, nextState.getValue().startedAt());
        assertEquals(0, recorder.successes.get());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    void observerEnrichedMetadataIsPassedToBusinessHandler() throws Exception {
        var recorder = new RecordingObserver(new EnrichingObserver(null));
        var seenMetadata = new AtomicReference<MessageMetadata>();
        var handler = handler((message, metadata) -> {
            seenMetadata.set(metadata);
            return true;
        }, (message, metadata) -> true, recorder);

        deliver(handler, false, Map.of("k", "v"), DELIVERY_TAG);

        assertEquals("true", seenMetadata.get().getHeaders().get(ENRICHED_HEADER));
        assertEquals("v", seenMetadata.get().getHeaders().get("k"));
        assertFalse(seenMetadata.get().isRedelivered());
        assertEquals(1, recorder.successes.get());
        assertEquals(0, recorder.failures.size());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void schedulingFailurePropagatesInsteadOfSettlingDelivery() throws Exception {
        var recorder = new RecordingObserver(new TerminalRMQObserver());
        var dropHandler = spy(new MessageDropHandler(new DropConfig()));
        var errorChecks = new AtomicInteger();
        var handler = handler((message, metadata) -> {
            throw new IOException("boom");
        }, (message, metadata) -> true, recorder, error -> {
            errorChecks.incrementAndGet();
            return true;
        }, dropHandler);
        doThrow(new io.appform.dropwizard.actors.retry.async.RetrySchedulingException(
                "broker unreachable", new IOException("broker unreachable"))).when(scheduler)
                .schedule(anyString(), any(byte[].class), any(), any(), anyLong());

        org.junit.jupiter.api.Assertions.assertThrows(
                io.appform.dropwizard.actors.retry.async.RetrySchedulingException.class,
                () -> deliver(handler, false, Map.of(), DELIVERY_TAG));

        verify(scheduler).schedule(anyString(), any(byte[].class), any(), any(), anyLong());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verify(channel, never()).abort();
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(channel, never()).basicCancel(anyString());
        verify(dropHandler, never()).handle();
        assertEquals(0, errorChecks.get());
        assertEquals(1, recorder.failures.size());
        assertEquals(0, recorder.successes.get());
    }

    @Test
    void expiredMessagesRouteToExpiredHandlerWithObserverStillSeeingAttempt() throws Exception {
        var recorder = new RecordingObserver(new TerminalRMQObserver());
        var freshCalls = new AtomicInteger();
        var handler = handler((message, metadata) -> {
            freshCalls.incrementAndGet();
            return true;
        }, (message, metadata) -> true, recorder);

        deliver(handler, false, Map.of(MESSAGE_EXPIRY_TEXT, System.currentTimeMillis() - 1L), DELIVERY_TAG);

        assertEquals(0, freshCalls.get());
        assertEquals(1, recorder.contexts.size());
        assertEquals(1, recorder.successes.get());
        verify(scheduler, never()).schedule(anyString(), any(), any(), any(), anyLong());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }
}
