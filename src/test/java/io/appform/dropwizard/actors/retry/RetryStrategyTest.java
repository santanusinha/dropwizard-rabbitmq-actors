package io.appform.dropwizard.actors.retry;

import com.github.rholder.retry.RetryException;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.retry.async.ActorRetryer;
import io.appform.dropwizard.actors.retry.async.AsyncRetryScheduler;
import io.appform.dropwizard.actors.retry.async.AsyncRetryer;
import io.appform.dropwizard.actors.retry.async.RetryState;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.impl.CountLimitedFixedWaitRetryStrategy;
import io.appform.dropwizard.actors.retry.impl.NoRetryStrategy;
import io.dropwizard.util.Duration;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetryStrategyTest {

    private final RetryStrategyFactory factory = new RetryStrategyFactory();

    @Test
    void syncRetriesUntilAttemptLimitIncludingInitialAttempt() {
        var strategy = factory.create(fixedWait(null));
        var attempts = new AtomicInteger();
        var error = assertThrows(RetryException.class, () -> strategy.execute(() -> {
            attempts.incrementAndGet();
            throw new IOException();
        }, null, null, null));
        assertEquals(3, attempts.get());
        assertEquals(3, error.getNumberOfFailedAttempts());
    }

    @Test
    void syncCanSucceedAfterRetry() throws Exception {
        var strategy = factory.create(fixedWait(null));
        var attempts = new AtomicInteger();
        assertTrue(strategy.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IOException();
            }
            return true;
        }, null, null, null));
        assertEquals(3, attempts.get());
    }

    @Test
    void nonRetriableExceptionsAndFalseResultsDoNotRetry() throws Exception {
        var strategy = factory.create(fixedWait(Set.of("IllegalStateException")));
        var attempts = new AtomicInteger();
        var error = assertThrows(ExecutionException.class, () -> strategy.execute(() -> {
            attempts.incrementAndGet();
            throw new IOException();
        }, null, null, null));
        assertInstanceOf(IOException.class, error.getCause());
        assertEquals(1, attempts.get());
        attempts.set(0);
        assertFalse(strategy.execute(() -> {
            attempts.incrementAndGet();
            return false;
        }, null, null, null));
        assertEquals(1, attempts.get());
    }

    @Test
    void noRetryPreservesResultsAndExceptionWrapping() throws Exception {
        var strategy = factory.create(new NoRetryConfig());
        assertTrue(strategy.execute(() -> true, null, null, null));
        assertFalse(strategy.execute(() -> false, null, null, null));
        var failure = new IOException("failure");
        var error = assertThrows(ExecutionException.class, () -> strategy.execute(() -> {
            throw failure;
        }, null, null, null));
        assertSame(failure, error.getCause());
    }

    @Test
    void boundAsyncExecutorReceivesDeliveryAndLifecycle() throws Exception {
        var scheduler = mock(AsyncRetryScheduler.class);
        var strategy = factory.create(fixedWait(null));
        strategy.setActorRetryer(new AsyncRetryer(strategy.getRetryPolicy(), scheduler));
        when(scheduler.restoreState(eq("queue"), any(), anyLong())).thenReturn(new RetryState(1, 0L));
        var body = new byte[]{1};
        var properties = new AMQP.BasicProperties();
        strategy.start();
        assertTrue(strategy.execute(() -> {
            throw new IOException();
        }, "queue", body, properties));
        strategy.stop();
        verify(scheduler).schedule("queue", body, properties, new RetryState(2, 0L), 1L);
        verify(scheduler).start();
        verify(scheduler).stop();
    }

    @Test
    void createRetainsConcreteStrategyTypeAndIndependentPolicies() {
        var first = factory.create(fixedWait(null));
        var second = factory.create(fixedWait(null));
        assertInstanceOf(CountLimitedFixedWaitRetryStrategy.class, first);
        assertInstanceOf(NoRetryStrategy.class, factory.create(new NoRetryConfig()));
        assertNotSame(first, second);
        assertNotSame(first.getRetryPolicy(), second.getRetryPolicy());
    }

    @Test
    void boundExecutorReceivesOriginalArgumentsAndReturnValue() throws Exception {
        var strategy = factory.create(fixedWait(null));
        var executor = mock(ActorRetryer.class);
        strategy.setActorRetryer(executor);
        var properties = new AMQP.BasicProperties();
        var body = new byte[]{1};
        java.util.concurrent.Callable<Boolean> callable = () -> true;
        when(executor.call(callable, "queue", body, properties)).thenReturn(false);
        assertFalse(strategy.execute(callable, "queue", body, properties));
        verify(executor).call(callable, "queue", body, properties);
    }

    @Test
    void directStrategySupportsSynchronousExecutionAndLifecycle() throws Exception {
        var strategy = new CountLimitedFixedWaitRetryStrategy(fixedWait(null));
        var attempts = new AtomicInteger();
        strategy.start();
        var error = assertThrows(RetryException.class, () -> strategy.execute(() -> {
            attempts.incrementAndGet();
            throw new IOException();
        }));
        assertEquals(3, attempts.get());
        assertEquals(3, error.getNumberOfFailedAttempts());
        assertTrue(strategy.execute(() -> true, null, null, null));
        strategy.stop();
    }

    private static CountLimitedFixedWaitRetryConfig fixedWait(Set<String> exceptions) {
        return CountLimitedFixedWaitRetryConfig.builder().maxAttempts(3)
                .waitTime(Duration.milliseconds(1)).retriableExceptions(exceptions).build();
    }
}
