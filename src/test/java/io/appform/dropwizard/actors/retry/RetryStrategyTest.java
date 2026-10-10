package io.appform.dropwizard.actors.retry;

import com.github.rholder.retry.RetryException;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.retry.async.ActorRetryer;
import io.appform.dropwizard.actors.retry.async.AsyncRetryScheduler;
import io.appform.dropwizard.actors.retry.async.RetryState;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import io.appform.dropwizard.actors.retry.impl.CountLimitedFixedWaitRetryStrategy;
import io.appform.dropwizard.actors.retry.impl.NoRetryStrategy;
import io.dropwizard.util.Duration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        var attempts = new AtomicInteger();

        assertTrue(strategy.execute(() -> true, null, null, null));
        assertFalse(strategy.execute(() -> false, null, null, null));
        var error = assertThrows(ExecutionException.class, () -> strategy.execute(() -> {
            attempts.incrementAndGet();
            throw new IOException();
        }, null, null, null));
        assertInstanceOf(IOException.class, error.getCause());
        assertEquals(1, attempts.get());
    }

    @Test
    void asyncStrategyUsesSuppliedScheduler() throws Exception {
        var scheduler = mock(AsyncRetryScheduler.class);
        var strategy = factory.create(async(fixedWait(null)), scheduler);
        var scheduledState = new AtomicReference<RetryState>();

        when(scheduler.restoreState(anyString(), any(), anyLong())).thenReturn(new RetryState(1, 0L));
        doAnswer(invocation -> {
            scheduledState.set(invocation.getArgument(3));
            return null;
        }).when(scheduler).schedule(anyString(), any(byte[].class), any(), any(), anyLong());

        assertTrue(strategy.execute(() -> {
            throw new IOException();
        }, "queue", new byte[0], new AMQP.BasicProperties()));
        verify(scheduler).schedule(eq("queue"), any(byte[].class), any(), any(RetryState.class), anyLong());
        assertEquals(new RetryState(2, 0L), scheduledState.get());
    }

    @Test
    void asyncStrategyWithoutSchedulerFailsFast() {
        var error = assertThrows(NullPointerException.class,
                () -> factory.create(async(fixedWait(null)), null));
        assertEquals("Async retry scheduler is required", error.getMessage());
    }

    @Test
    void asyncStrategyViaSingleArgFactoryMethodWithoutSchedulerFailsFast() {
        assertThrows(NullPointerException.class, () -> factory.create(async(fixedWait(null))));
    }

    @Test
    void syncStrategyIgnoresSuppliedScheduler() throws Exception {
        var strategy = factory.create(fixedWait(null), mock(AsyncRetryScheduler.class));
        var attempts = new AtomicInteger();

        assertTrue(strategy.execute(() -> {
            attempts.incrementAndGet();
            return true;
        }, null, null, null));
        assertEquals(1, attempts.get());
    }

    @Test
    void createRetainsConcreteStrategyType() {
        assertInstanceOf(CountLimitedFixedWaitRetryStrategy.class, factory.create(fixedWait(null)));
        assertInstanceOf(NoRetryStrategy.class, factory.create(new NoRetryConfig()));
    }

    @Test
    void protectedHookAllowsCustomExecutor() throws Exception {
        var customFactory = new RecordingFactory();
        var strategy = customFactory.create(fixedWait(null));

        assertTrue(strategy.execute(() -> true, "queue", null, null));

        assertEquals(1, customFactory.created.size());
        assertEquals(List.of("call:queue"), customFactory.created.get(0).events);
    }

    @Test
    void lifecycleDelegatesToBoundExecutor() throws Exception {
        var customFactory = new RecordingFactory();
        var strategy = customFactory.create(fixedWait(null));

        strategy.start();
        strategy.stop();

        assertEquals(List.of("start", "stop"), customFactory.created.get(0).events);
    }

    @Test
    void factoryReuseBindsFreshExecutors() throws Exception {
        var customFactory = new RecordingFactory();
        var first = customFactory.create(fixedWait(null));
        var second = customFactory.create(fixedWait(null));

        assertNotSame(first, second);
        assertEquals(2, customFactory.created.size());
        assertNotSame(customFactory.created.get(0), customFactory.created.get(1));
        assertTrue(first.execute(() -> true, null, null, null));
        assertTrue(second.execute(() -> true, null, null, null));
        assertEquals(List.of("call:null"), customFactory.created.get(0).events);
        assertEquals(List.of("call:null"), customFactory.created.get(1).events);
    }

    @Test
    void setActorRetryerIsSingleAssignment() throws Exception {
        RetryStrategy strategy = new CountLimitedFixedWaitRetryStrategy(fixedWait(null));
        strategy.setActorRetryer((callable, queueName, body, properties) -> callable.call());

        var error = assertThrows(IllegalStateException.class,
                () -> strategy.setActorRetryer((callable, queueName, body, properties) -> true));
        assertEquals("Retry executor has already been bound", error.getMessage());
    }

    @Test
    void setActorRetryerRejectsNull() {
        RetryStrategy strategy = new CountLimitedFixedWaitRetryStrategy(fixedWait(null));

        var error = assertThrows(NullPointerException.class, () -> strategy.setActorRetryer(null));
        assertEquals("actorRetryer", error.getMessage());
    }

    @Test
    void unboundDirectStrategyFails() {
        RetryStrategy strategy = new CountLimitedFixedWaitRetryStrategy(fixedWait(null));

        var executeError = assertThrows(IllegalStateException.class,
                () -> strategy.execute(() -> true, null, null, null));
        assertEquals("Retry executor must be bound by RetryStrategyFactory", executeError.getMessage());
        assertThrows(IllegalStateException.class, strategy::start);
        assertThrows(IllegalStateException.class, strategy::stop);
    }

    @Test
    void nullModeFailsDefaultHook() {
        var config = fixedWait(null);
        config.setRetryMode(null);
        var policy = new CountLimitedFixedWaitRetryStrategy(fixedWait(null)).getPolicy();

        var error = assertThrows(NullPointerException.class,
                () -> factory.createActorRetryer(config, policy, null));
        assertEquals("retryMode", error.getMessage());
    }

    private static final class RecordingRetryer implements ActorRetryer {
        private final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void start() {
            events.add("start");
        }

        @Override
        public void stop() {
            events.add("stop");
        }

        @Override
        public boolean call(Callable<Boolean> callable, String queueName, byte[] body,
                            AMQP.BasicProperties properties) throws Exception {
            events.add("call:" + queueName);
            return callable.call();
        }
    }

    private static final class RecordingFactory extends RetryStrategyFactory {
        private final List<RecordingRetryer> created = new ArrayList<>();

        @Override
        protected ActorRetryer createActorRetryer(RetryConfig config,
                                                  ActorRetryerBuilder.Policy<Boolean> policy,
                                                  AsyncRetryScheduler scheduler) {
            var retryer = new RecordingRetryer();
            created.add(retryer);
            return retryer;
        }
    }

    private static CountLimitedFixedWaitRetryConfig async(CountLimitedFixedWaitRetryConfig config) {
        config.setRetryMode(RetryMode.ASYNC);
        return config;
    }

    private static CountLimitedFixedWaitRetryConfig fixedWait(Set<String> exceptions) {
        return CountLimitedFixedWaitRetryConfig.builder()
                .maxAttempts(3)
                .waitTime(Duration.milliseconds(1))
                .retriableExceptions(exceptions)
                .build();
    }
}
