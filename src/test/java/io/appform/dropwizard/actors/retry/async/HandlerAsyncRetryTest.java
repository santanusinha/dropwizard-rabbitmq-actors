package io.appform.dropwizard.actors.retry.async;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.base.Handler;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.config.DropConfig;
import io.appform.dropwizard.actors.exceptionhandler.config.SidelineConfig;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.exceptionhandler.handlers.MessageDropHandler;
import io.appform.dropwizard.actors.exceptionhandler.handlers.MessageSidelineHandler;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.WaitMode;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private Handler<TestMessage> handler(Function<Throwable, Boolean> errorCheck,
                                         ExceptionHandler exceptionHandler) throws Exception {
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
    void retrySchedulingFailurePropagatesWithoutSettlingDeliveryOrApplyingDropPolicy() throws Exception {
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

        assertThrows(RetrySchedulingException.class, () -> deliver(dropPolicyHandler, DELIVERY_TAG));

        assertFalse(errorCheckInvoked.get());
        verify(dropHandler, never()).handle();
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verify(channel, never()).abort();
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(channel, never()).basicCancel(anyString());
        assertFalse(dropPolicyHandler.isRunning());
        dropPolicyHandler.awaitCompletion();
    }

    @Test
    void expiredDeliveryUsesExpiredHandler() throws Exception {
        var scheduler = mock(AsyncRetryScheduler.class);
        when(scheduler.restoreState(anyString(), any(), anyLong()))
                .thenAnswer(invocation -> new RetryState(1, invocation.getArgument(2)));
        var config = new CountLimitedFixedWaitRetryConfig();
        config.setWaitMode(WaitMode.ASYNC);
        config.setMaxAttempts(3);
        var strategy = new RetryStrategyFactory().create(config);
        strategy.setActorRetryer(new AsyncRetryer(strategy.getRetryPolicy(), scheduler));
        var handled = new AtomicBoolean();
        var expired = new AtomicBoolean();
        var target = new Handler<>(channel, mapper, TestMessage.class, 1, error -> false, strategy,
                new MessageSidelineHandler(new SidelineConfig()), (message, metadata) -> {
                    handled.set(true);
                    return true;
                }, (message, metadata) -> {
                    expired.set(true);
                    return true;
                }, new TerminalRMQObserver(), QUEUE);
        strategy.start();
        try {
            var properties = new AMQP.BasicProperties.Builder().headers(Map.of(
                    Constants.MESSAGE_EXPIRY_TEXT, System.currentTimeMillis() - 1)).build();

            target.handleDelivery("consumer-tag", new Envelope(DELIVERY_TAG, false, "exchange", QUEUE),
                    properties, body);

            assertFalse(handled.get());
            assertTrue(expired.get());
            verify(channel).basicAck(DELIVERY_TAG, false);
            verify(channel, never()).basicReject(anyLong(), anyBoolean());
        } finally {
            strategy.stop();
        }
    }

    @Test
    void realRetryChainPropagatesWhenBrokerCannotConfirmHandoff() throws Exception {
        var connection = mock(RMQConnection.class);
        var topology = mock(Channel.class);
        var publisher = mock(Channel.class);
        when(publisher.isOpen()).thenReturn(true);
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.newChannel()).thenReturn(topology, publisher);
        var config = new CountLimitedFixedWaitRetryConfig();
        config.setWaitMode(WaitMode.ASYNC);
        config.setMaxAttempts(4);
        var strategy = new RetryStrategyFactory().create(config, new ActorConfig(), connection, "exchange", QUEUE);
        doThrow(new IOException("broker unavailable")).when(publisher)
                .waitForConfirmsOrDie(RetryStrategyFactory.DEFAULT_CONFIRM_TIMEOUT_MS);
        var realHandler = new Handler<>(channel, mapper, TestMessage.class, 1, error -> false, strategy,
                new MessageSidelineHandler(new SidelineConfig()), (message, metadata) -> {
                    throw new IOException("business failure");
                }, (message, metadata) -> true, new TerminalRMQObserver(), QUEUE);
        strategy.start();
        try {
            var failure = assertThrows(RetrySchedulingException.class, () -> deliver(realHandler, DELIVERY_TAG));
            assertInstanceOf(IOException.class, failure.getCause());
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
            verify(channel, never()).basicReject(anyLong(), anyBoolean());
            assertFalse(realHandler.isRunning());
            verify(publisher).basicPublish(anyString(), eq(QUEUE), eq(true), any(), any(byte[].class));
        } finally {
            strategy.stop();
        }
    }

    @Test
    void permanentSchedulingRejectionUsesSidelinePolicyWithoutRequeue() throws Exception {
        when(retryStrategy.execute(any(), eq(QUEUE), any(byte[].class), any()))
                .thenThrow(new RetrySchedulingRejectedException("Delay level limit reached"));

        deliver(handler, DELIVERY_TAG);

        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertFalse(handler.isRunning());
    }

    @Test
    void permanentSchedulingRejectionUsesDropPolicyWithoutRequeue() throws Exception {
        var dropHandler = handler(error -> false, new MessageDropHandler(new DropConfig()));
        when(retryStrategy.execute(any(), eq(QUEUE), any(byte[].class), any()))
                .thenThrow(new RetrySchedulingRejectedException("Delay level limit reached"));

        deliver(dropHandler, DELIVERY_TAG);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertFalse(dropHandler.isRunning());
    }

    @Test
    void basicAckFailurePropagatesWithoutErrorCheckOrExceptionHandler() throws Exception {
        var errorCheckInvoked = new AtomicBoolean(false);
        var dropHandler = spy(new MessageDropHandler(new DropConfig()));
        var ackFailureHandler = handler(error -> {
            errorCheckInvoked.set(true);
            return true;
        }, dropHandler);
        when(retryStrategy.execute(any(), eq(QUEUE), any(byte[].class), any()))
                .thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
        doThrow(new IOException("channel closed before ack")).when(channel).basicAck(DELIVERY_TAG, false);

        assertThrows(IOException.class, () -> deliver(ackFailureHandler, DELIVERY_TAG));

        assertFalse(errorCheckInvoked.get());
        verify(dropHandler, never()).handle();
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertFalse(ackFailureHandler.isRunning());
        ackFailureHandler.awaitCompletion();
    }

    @Test
    void basicRejectFailurePropagatesWithoutErrorCheckOrExceptionHandler() throws Exception {
        var errorCheckInvoked = new AtomicBoolean(false);
        var dropHandler = spy(new MessageDropHandler(new DropConfig()));
        var rejectFailureHandler = handler(error -> {
            errorCheckInvoked.set(true);
            return true;
        }, dropHandler);
        when(retryStrategy.execute(any(), eq(QUEUE), any(byte[].class), any())).thenReturn(false);
        doThrow(new IOException("channel closed before reject")).when(channel).basicReject(DELIVERY_TAG, false);

        assertThrows(IOException.class,
                () -> deliver(rejectFailureHandler, DELIVERY_TAG));

        assertFalse(errorCheckInvoked.get());
        verify(dropHandler, never()).handle();
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertFalse(rejectFailureHandler.isRunning());
        rejectFailureHandler.awaitCompletion();
    }
}
