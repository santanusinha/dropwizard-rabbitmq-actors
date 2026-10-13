package io.appform.dropwizard.actors.retry.async;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.retry.ActorRetryerBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsyncRetryerTest {

    private static final String QUEUE = "queue";
    private static final long STARTED_AT = 1_000L;

    private final AsyncRetryScheduler scheduler = mock(AsyncRetryScheduler.class);
    private final FakeClock clock = new FakeClock(STARTED_AT);
    private final byte[] body = new byte[]{1, 2};

    private static final class FakeClock extends AtomicLong {
        private FakeClock(long now) {
            super(now);
        }
    }

    private static final class FailingCallable implements Callable<Boolean> {
        int invocations;

        @Override
        public Boolean call() {
            invocations++;
            throw new IllegalStateException("unreachable");
        }
    }

    private AsyncRetryer retryer(ActorRetryerBuilder.Policy<Boolean> policy) {
        return new AsyncRetryer(policy, scheduler, clock::get);
    }

    private void restore(RetryState state) {
        when(scheduler.restoreState(eq(QUEUE), any(), anyLong())).thenReturn(state);
    }

    private void scheduleAndFailOnce(AsyncRetryer retryer, RetryState state) throws Exception {
        restore(state);
        var attempts = new AtomicInteger();
        assertTrue(retryer.call(() -> {
            attempts.incrementAndGet();
            throw new IOException();
        }, QUEUE, body, new AMQP.BasicProperties()));
        assertEquals(1, attempts.get());
    }

    @Test
    void successfulAndFalseResultsAreTerminal() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(error -> true).build());
        restore(new RetryState(1, STARTED_AT));

        assertTrue(retryer.call(() -> true, QUEUE, body, new AMQP.BasicProperties()));
        assertFalse(retryer.call(() -> false, QUEUE, body, new AMQP.BasicProperties()));
        verify(scheduler, never()).schedule(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void nonRetriableExceptionIsWrappedAndDoesNotSchedule() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(IllegalStateException.class::isInstance).build());
        var failure = new IOException("not retryable");
        restore(new RetryState(1, STARTED_AT));

        var error = assertThrows(ExecutionException.class,
                () -> retryer.call(() -> {
                    throw failure;
                }, QUEUE, body, new AMQP.BasicProperties()));

        assertSame(failure, error.getCause());
        verify(scheduler, never()).schedule(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void defaultPolicyDoesNotRetryExceptions() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder().build());
        restore(new RetryState(1, STARTED_AT));

        assertThrows(ExecutionException.class,
                () -> retryer.call(() -> {
                    throw new IOException();
                }, QUEUE, body, new AMQP.BasicProperties()));
        verify(scheduler, never()).schedule(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void eligibleFailureExecutesOnceAndSchedulesOriginalArgumentsWithNextState() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(IOException.class::isInstance).build());
        var properties = new AMQP.BasicProperties();
        restore(new RetryState(1, STARTED_AT));

        var attempts = new AtomicInteger();
        assertTrue(retryer.call(() -> {
            attempts.incrementAndGet();
            throw new IOException();
        }, QUEUE, body, properties));
        assertEquals(1, attempts.get());

        verify(scheduler).restoreState(QUEUE, properties, STARTED_AT);
        verify(scheduler).schedule(eq(QUEUE), same(body), same(properties),
                eq(new RetryState(2, STARTED_AT)), eq(0L));
        verifyNoMoreInteractions(scheduler);
    }

    @Test
    void schedulerReceivesPolicyDelay() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(error -> true)
                .withWaitStrategy(WaitStrategies.fixedWait(100, TimeUnit.MILLISECONDS)).build());

        scheduleAndFailOnce(retryer, new RetryState(1, STARTED_AT));

        verify(scheduler).schedule(eq(QUEUE), same(body), any(), any(), eq(100L));
    }

    @Test
    void schedulingFailureIsNotSwallowedOrReclassified() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(error -> true).build());
        var failure = new IOException("publish failed");
        doThrow(failure).when(scheduler).schedule(eq(QUEUE), same(body), any(), any(), anyLong());
        restore(new RetryState(1, STARTED_AT));

        var error = assertThrows(RetrySchedulingException.class,
                () -> retryer.call(() -> {
                    throw new IllegalStateException();
                }, QUEUE, body, new AMQP.BasicProperties()));

        assertSame(failure, error.getCause());
    }

    @Test
    void interruptedSchedulingRestoresInterruptFlag() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(error -> true).build());
        doThrow(new InterruptedException()).when(scheduler)
                .schedule(eq(QUEUE), same(body), any(), any(), anyLong());
        restore(new RetryState(1, STARTED_AT));

        var error = assertThrows(RetrySchedulingException.class,
                () -> retryer.call(() -> {
                    throw new IOException();
                }, QUEUE, body, new AMQP.BasicProperties()));

        assertTrue(error.getCause() instanceof InterruptedException);
        assertTrue(Thread.interrupted());
    }

    @Test
    void countBudgetExhaustionThrowsRetryExceptionWithOriginalAttempt() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(error -> true)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3)).build());

        scheduleAndFailOnce(retryer, new RetryState(1, STARTED_AT));
        verify(scheduler, times(1)).schedule(eq(QUEUE), same(body), any(), any(), anyLong());

        scheduleAndFailOnce(retryer, new RetryState(2, STARTED_AT));
        verify(scheduler, times(2)).schedule(eq(QUEUE), same(body), any(), any(), anyLong());

        restore(new RetryState(3, STARTED_AT));
        var error = assertThrows(RetryException.class,
                () -> retryer.call(() -> {
                    throw new IOException();
                }, QUEUE, body, new AMQP.BasicProperties()));

        assertEquals(3, error.getNumberOfFailedAttempts());
        assertEquals(3L, error.getLastFailedAttempt().getAttemptNumber());
        assertTrue(error.getLastFailedAttempt().getExceptionCause() instanceof IOException);
        verify(scheduler, times(2)).schedule(eq(QUEUE), same(body), any(), any(), anyLong());
    }

    @Test
    void timeBudgetUsesWallClockSinceFirstAttemptWithInjectedClock() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(error -> true)
                .withStopStrategy(StopStrategies.stopAfterDelay(5_000L)).build());

        scheduleAndFailOnce(retryer, new RetryState(1, STARTED_AT));
        verify(scheduler, times(1)).schedule(eq(QUEUE), same(body), any(), any(), anyLong());

        clock.set(4_000L);
        scheduleAndFailOnce(retryer, new RetryState(2, 500L));
        verify(scheduler, times(2)).schedule(eq(QUEUE), same(body), any(), any(), anyLong());

        clock.set(6_000L);
        restore(new RetryState(3, 500L));
        var error = assertThrows(RetryException.class,
                () -> retryer.call(() -> {
                    throw new IOException();
                }, QUEUE, body, new AMQP.BasicProperties()));

        assertEquals(5_500L, error.getLastFailedAttempt().getDelaySinceFirstAttempt());
        verify(scheduler, times(2)).schedule(eq(QUEUE), same(body), any(), any(), anyLong());
    }

    @Test
    void incrementalDelaysGrowWithRestoredAttemptCount() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(error -> true)
                .withWaitStrategy(WaitStrategies.incrementingWait(100, TimeUnit.MILLISECONDS,
                        250, TimeUnit.MILLISECONDS)).build());

        scheduleAndFailOnce(retryer, new RetryState(1, STARTED_AT));
        verify(scheduler).schedule(eq(QUEUE), same(body), any(), eq(new RetryState(2, STARTED_AT)), eq(100L));

        scheduleAndFailOnce(retryer, new RetryState(2, STARTED_AT));
        verify(scheduler).schedule(eq(QUEUE), same(body), any(), eq(new RetryState(3, STARTED_AT)), eq(350L));

        scheduleAndFailOnce(retryer, new RetryState(3, STARTED_AT));
        verify(scheduler).schedule(eq(QUEUE), same(body), any(), eq(new RetryState(4, STARTED_AT)), eq(600L));
    }

    @Test
    void stateIsFetchedBeforeCallableRuns() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(error -> true).build());
        restore(new RetryState(2, STARTED_AT));

        var callable = new FailingCallable();
        assertTrue(retryer.call(() -> {
            assertEquals(0, callable.invocations);
            throw new IOException();
        }, QUEUE, body, new AMQP.BasicProperties()));
    }

    @Test
    void clockAdvancesElapsedWhileCallableRuns() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(error -> true)
                .withStopStrategy(StopStrategies.stopAfterDelay(1_000L)).build());
        restore(new RetryState(4, 2_000L));
        clock.set(2_000L);

        var error = assertThrows(RetryException.class,
                () -> retryer.call(() -> {
                    clock.set(7_500L);
                    throw new IOException();
                }, QUEUE, body, new AMQP.BasicProperties()));

        assertEquals(5_500L, error.getLastFailedAttempt().getDelaySinceFirstAttempt());
        verify(scheduler, never()).schedule(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void nullSchedulerFailsDescriptivelyBeforeAnyWork() {
        var retryer = ActorRetryerBuilder.<Boolean>newBuilder().build();
        var error = assertThrows(NullPointerException.class,
                () -> new AsyncRetryer(retryer, null, clock::get));

        assertEquals("asyncRetryScheduler", error.getMessage());
    }

    @Test
    void lifecycleDelegatesToScheduler() throws Exception {
        var retryer = retryer(ActorRetryerBuilder.<Boolean>newBuilder().build());

        retryer.start();
        retryer.stop();

        verify(scheduler).start();
        verify(scheduler).stop();
    }
}
