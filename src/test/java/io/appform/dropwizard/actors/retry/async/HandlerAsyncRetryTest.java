package io.appform.dropwizard.actors.retry.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.base.Handler;
import io.appform.dropwizard.actors.exceptionhandler.config.DropConfig;
import io.appform.dropwizard.actors.exceptionhandler.config.SidelineConfig;
import io.appform.dropwizard.actors.exceptionhandler.handlers.MessageDropHandler;
import io.appform.dropwizard.actors.exceptionhandler.handlers.MessageSidelineHandler;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock-based tests for the {@link Handler} async-retry handoff contract.
 */
class HandlerAsyncRetryTest {

    private static final String QUEUE = "handler.test.queue";
    private static final long DELIVERY_TAG = 7L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Channel channel = mock(Channel.class);
    private final RetryStrategy retryStrategy = mock(RetryStrategy.class);

    public static class TestMessage {
        public String id = "m-1";
    }

    private Handler<TestMessage> handler;
    private byte[] body;

    @BeforeEach
    void setUp() throws Exception {
        body = mapper.writeValueAsBytes(new TestMessage());
        handler = handler(error -> false, new MessageSidelineHandler(new SidelineConfig()));
    }

    private Handler<TestMessage> handler(java.util.function.Function<Throwable, Boolean> errorCheck,
                                         io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler exceptionHandler) throws Exception {
        return new Handler<>(channel, mapper, TestMessage.class, 1,
                errorCheck, retryStrategy, exceptionHandler,
                (message, metadata) -> true, (message, metadata) -> true,
                new TerminalRMQObserver(), QUEUE);
    }

    private void deliver(Handler<TestMessage> target, long deliveryTag) throws IOException {
        target.handleDelivery("consumer-tag", new Envelope(deliveryTag, false, "test.exchange", QUEUE),
                new AMQP.BasicProperties(), body);
    }

    @Test
    void successfulRetryHandoffAcksDeliveryAndResetsRunningFlag() throws Exception {
        when(retryStrategy.execute(any(), eq(QUEUE), any(byte[].class), any()))
                .thenAnswer(invocation -> {
                    assertTrue(handler.isRunning());
                    return ((Callable<?>) invocation.getArgument(0)).call();
                });

        deliver(handler, DELIVERY_TAG);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(channel, never()).abort();
        verify(channel, never()).basicCancel(anyString());
        assertFalse(handler.isRunning());
        handler.awaitCompletion();
    }

    @Test
    void businessExhaustionRejectsWithDefaultExceptionPolicy() throws Exception {
        var observed = new AtomicReference<Throwable>();
        var defaultPolicyHandler = handler(error -> {
            observed.set(error);
            return false;
        }, new MessageSidelineHandler(new SidelineConfig()));
        when(retryStrategy.execute(any(), eq(QUEUE), any(byte[].class), any()))
                .thenAnswer(invocation -> {
                    assertTrue(defaultPolicyHandler.isRunning());
                    throw new RetryException(3, mock(Attempt.class));
                });

        deliver(defaultPolicyHandler, DELIVERY_TAG);

        assertInstanceOf(RetryException.class, observed.get());
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, never()).abort();
        assertFalse(defaultPolicyHandler.isRunning());
        defaultPolicyHandler.awaitCompletion();
    }

    @Test
    void retrySchedulingFailureRequeuesDeliveryDespiteErrorCheckAndDropHandler() throws Exception {
        var errorCheckInvoked = new AtomicBoolean(false);
        var dropHandler = spy(new MessageDropHandler(new DropConfig()));
        var dropPolicyHandler = handler(error -> {
            errorCheckInvoked.set(true);
            return true;
        }, dropHandler);
        when(retryStrategy.execute(any(), eq(QUEUE), any(byte[].class), any()))
                .thenAnswer(invocation -> {
                    assertTrue(dropPolicyHandler.isRunning());
                    throw new RetrySchedulingException("Retry handoff failed", new IOException("broker unreachable"));
                });

        deliver(dropPolicyHandler, DELIVERY_TAG);

        assertFalse(errorCheckInvoked.get());
        verify(dropHandler, never()).handle();
        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).abort();
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(channel, never()).basicCancel(anyString());
        assertFalse(dropPolicyHandler.isRunning());
        dropPolicyHandler.awaitCompletion();
    }
}
