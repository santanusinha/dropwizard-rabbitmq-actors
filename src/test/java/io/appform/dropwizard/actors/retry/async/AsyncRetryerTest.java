package io.appform.dropwizard.actors.retry.async;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.retry.ActorRetryerBuilder;
import io.appform.dropwizard.actors.retry.RetryPolicy;
import io.appform.dropwizard.actors.retry.WaitMode;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsyncRetryerTest {
    private static final String QUEUE = "queue";
    private static final long STARTED_AT = 1000;
    private final AsyncRetryScheduler scheduler = mock(AsyncRetryScheduler.class);
    private final Clock clock = mock(Clock.class);
    private final byte[] body = {1, 2};
    private final AMQP.BasicProperties properties = new AMQP.BasicProperties();

    @BeforeEach
    void setUp() {
        when(clock.millis()).thenReturn(STARTED_AT);
        when(scheduler.restoreState(QUEUE, properties, STARTED_AT)).thenReturn(new RetryState(1, STARTED_AT));
    }

    private AsyncRetryer retryer(RetryPolicy<Boolean> policy) {
        return new AsyncRetryer(policy, scheduler, clock);
    }

    private RetryPolicy<Boolean> policy() {
        return ActorRetryerBuilder.<Boolean>newBuilder().retryIfException(error -> true)
                .withStopStrategy(StopStrategies.stopAfterAttempt(4))
                .withWaitStrategy(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS)).build();
    }

    @Test
    void successfulAndFalseResultsAreTerminal() throws Exception {
        var retryer = retryer(policy());
        assertTrue(retryer.call(() -> true, QUEUE, body, properties));
        assertFalse(retryer.call(() -> false, QUEUE, body, properties));
        verify(scheduler, never()).schedule(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void nonRetriableExceptionPreservesOriginalCause() {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(IllegalStateException.class::isInstance).build());
        var failure = new IOException("not retryable");
        var error = assertThrows(ExecutionException.class, () -> retryer.call(() -> {
            throw failure;
        }, QUEUE, body, properties));
        assertSame(failure, error.getCause());
    }

    @Test
    void eligibleFailureExecutesOnceAndSchedulesOriginalDelivery() throws Exception {
        var retryer = retryer(policy());
        var attempts = new AtomicInteger();
        assertTrue(retryer.call(() -> {
            attempts.incrementAndGet();
            throw new IOException();
        }, QUEUE, body, properties));
        assertEquals(1, attempts.get());
        verify(scheduler).schedule(QUEUE, body, properties, new RetryState(2, STARTED_AT), 100);
    }

    @Test
    void exhaustedCountPreservesFailedAttemptWithoutScheduling() throws Exception {
        when(scheduler.restoreState(QUEUE, properties, STARTED_AT)).thenReturn(new RetryState(4, STARTED_AT));
        var failure = new IOException("business failure");
        var error = assertThrows(RetryException.class, () -> retryer(policy()).call(() -> {
            throw failure;
        }, QUEUE, body, properties));
        assertEquals(4, error.getNumberOfFailedAttempts());
        assertSame(failure, error.getLastFailedAttempt().getExceptionCause());
        verify(scheduler, never()).schedule(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void timeBudgetIncludesCallableExecution() {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder().retryIfException(error -> true)
                .withStopStrategy(StopStrategies.stopAfterDelay(1000)).build();
        var error = assertThrows(RetryException.class, () -> retryer(policy).call(() -> {
            when(clock.millis()).thenReturn(2500L);
            throw new IOException();
        }, QUEUE, body, properties));
        assertEquals(1500, error.getLastFailedAttempt().getDelaySinceFirstAttempt());
    }

    @Test
    void futureStartClampsElapsedWithoutResettingProgress() throws Exception {
        when(scheduler.restoreState(QUEUE, properties, STARTED_AT)).thenReturn(new RetryState(2, 1500));
        var policy = ActorRetryerBuilder.<Boolean>newBuilder().retryIfException(error -> true)
                .withStopStrategy(attempt -> {
                    assertEquals(0, attempt.getDelaySinceFirstAttempt());
                    assertEquals(2, attempt.getAttemptNumber());
                    return false;
                }).build();
        retryer(policy).call(() -> { throw new IOException(); }, QUEUE, body, properties);
        verify(scheduler).schedule(QUEUE, body, properties, new RetryState(3, 1500), 0);
    }

    @Test
    void schedulingFailuresPropagateUnchanged() throws Exception {
        var failure = new RetrySchedulingException("handoff failed", new IOException());
        doThrow(failure).when(scheduler).schedule(QUEUE, body, properties, new RetryState(2, STARTED_AT), 100);
        assertSame(failure, assertThrows(RetrySchedulingException.class, () -> retryer(policy()).call(() -> {
            throw new IOException();
        }, QUEUE, body, properties)));
    }

    @Test
    void permanentSchedulingRejectionIsNotConvertedIntoTransportFailure() throws Exception {
        var failure = new RetrySchedulingRejectedException("unsupported delay");
        doThrow(failure).when(scheduler).schedule(QUEUE, body, properties, new RetryState(2, STARTED_AT), 100);
        assertSame(failure, assertThrows(RetrySchedulingRejectedException.class, () -> retryer(policy()).call(() -> {
            throw new IOException();
        }, QUEUE, body, properties)));
    }

    @Test
    void interruptedSchedulingPreservesInterruptFlag() throws Exception {
        doThrow(new InterruptedException()).when(scheduler)
                .schedule(QUEUE, body, properties, new RetryState(2, STARTED_AT), 100);
        try {
            var error = assertThrows(RetrySchedulingException.class, () -> retryer(policy()).call(() -> {
                throw new IOException();
            }, QUEUE, body, properties));
            assertInstanceOf(InterruptedException.class, error.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void restoredAttemptDeterminesIncrementalDelay() throws Exception {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder().retryIfException(error -> true)
                .withWaitStrategy(WaitStrategies.incrementingWait(100, TimeUnit.MILLISECONDS,
                        250, TimeUnit.MILLISECONDS)).build();
        when(scheduler.restoreState(QUEUE, properties, STARTED_AT)).thenReturn(new RetryState(3, STARTED_AT));
        retryer(policy).call(() -> { throw new IOException(); }, QUEUE, body, properties);
        verify(scheduler).schedule(QUEUE, body, properties, new RetryState(4, STARTED_AT), 600);
    }

    @Test
    void lifecycleDelegatesToScheduler() throws Exception {
        var retryer = retryer(policy());
        retryer.start();
        retryer.stop();
        verify(scheduler).start();
        verify(scheduler).stop();
        assertEquals(WaitMode.ASYNC, retryer.getRetryMode());
    }

    @Test
    void planningCollectsUniqueIncrementalDelays() throws Exception {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterAttempt(4))
                .withWaitStrategy(WaitStrategies.incrementingWait(100, TimeUnit.MILLISECONDS,
                        250, TimeUnit.MILLISECONDS)).build();
        assertEquals(Set.of(100L, 350L, 600L), AsyncRetryer.getPossibleDelays(policy, 10, true));
    }

    @Test
    void planningDeduplicatesCappedExponentialDelays() throws Exception {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterAttempt(4))
                .withWaitStrategy(WaitStrategies.exponentialWait(2000, 8000, TimeUnit.MILLISECONDS)).build();
        assertEquals(Set.of(4000L, 8000L), AsyncRetryer.getPossibleDelays(policy, 10, true));
    }

    @Test
    void timeLimitedPlanningIncludesWaitsUntilBudgetExhausted() throws Exception {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterDelay(1000))
                .withWaitStrategy(WaitStrategies.incrementingWait(100, TimeUnit.MILLISECONDS,
                        100, TimeUnit.MILLISECONDS)).build();
        assertEquals(Set.of(100L, 200L, 300L, 400L), AsyncRetryer.getPossibleDelays(policy, 10, true));
    }

    @Test
    void singleAttemptPlansNoDelays() throws Exception {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterAttempt(1)).build();
        assertTrue(AsyncRetryer.getPossibleDelays(policy, 10, true).isEmpty());
    }

    @Test
    void planningBudgetCanRejectOrReturnPartialDelays() throws Exception {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder()
                .withWaitStrategy(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS)).build();
        assertThrows(RetrySchedulingRejectedException.class, () -> AsyncRetryer.getPossibleDelays(policy, 10, true));
        assertEquals(Set.of(100L), AsyncRetryer.getPossibleDelays(policy, 10, false));
    }
}
