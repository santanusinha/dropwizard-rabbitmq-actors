package io.appform.dropwizard.actors.retry;

import io.appform.dropwizard.actors.retry.config.*;
import io.appform.dropwizard.actors.retry.impl.*;
import io.dropwizard.util.Duration;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VER-03: Asserts exact attempt counts for all strategy types by constructing
 * the actual strategy objects and calling execute() with a counting Callable.
 */
class RetryAttemptCountTest {

    @Test
    void countLimitedFixedWaitStopsAfterMaxAttempts() {
        var config = CountLimitedFixedWaitRetryConfig.builder()
                .maxAttempts(3)
                .waitTime(Duration.milliseconds(1))
                .retriableExceptions(null)
                .build();
        AtomicInteger count = new AtomicInteger(0);
        Callable<Boolean> alwaysFails = () -> {
            count.incrementAndGet();
            throw new RuntimeException("fail");
        };
        var strategy = new CountLimitedFixedWaitRetryStrategy(config);
        assertThrows(Exception.class, () -> strategy.execute(alwaysFails));
        assertEquals(3, count.get());
    }

    @Test
    void countLimitedExponentialStopsAfterMaxAttempts() {
        var config = CountLimitedExponentialWaitRetryConfig.builder()
                .maxAttempts(4)
                .maxTimeBetweenRetries(Duration.milliseconds(500))
                .multipier(1)
                .retriableExceptions(null)
                .build();
        AtomicInteger count = new AtomicInteger(0);
        Callable<Boolean> alwaysFails = () -> {
            count.incrementAndGet();
            throw new RuntimeException("fail");
        };
        var strategy = new CountLimitedExponentialWaitRetryStrategy(config);
        assertThrows(Exception.class, () -> strategy.execute(alwaysFails));
        assertEquals(4, count.get());
    }

    @Test
    void countLimitedIncrementalStopsAfterMaxAttempts() {
        var config = CountLimitedIncrementalWaitRetryConfig.builder()
                .maxAttempts(3)
                .initialWaitTime(Duration.milliseconds(1))
                .waitIncrement(Duration.milliseconds(1))
                .retriableExceptions(null)
                .build();
        AtomicInteger count = new AtomicInteger(0);
        Callable<Boolean> alwaysFails = () -> {
            count.incrementAndGet();
            throw new RuntimeException("fail");
        };
        var strategy = new CountLimitedIncrementalWaitRetryStrategy(config);
        assertThrows(Exception.class, () -> strategy.execute(alwaysFails));
        assertEquals(3, count.get());
    }

    @Test
    void noRetryStrategyExecutesExactlyOnce() {
        var config = new NoRetryConfig();
        AtomicInteger count = new AtomicInteger(0);
        Callable<Boolean> alwaysFails = () -> {
            count.incrementAndGet();
            throw new RuntimeException("fail");
        };
        var strategy = new NoRetryStrategy(config);
        assertThrows(Exception.class, () -> strategy.execute(alwaysFails));
        assertEquals(1, count.get());
    }

    @Test
    void timeLimitedFixedWaitExceedsDefaultAttemptCap() {
        var config = TimeLimitedFixedWaitRetryConfig.builder()
                .maxTime(Duration.milliseconds(5))
                .waitTime(Duration.milliseconds(1))
                .retriableExceptions(null)
                .build();
        AtomicInteger count = new AtomicInteger(0);
        Callable<Boolean> alwaysFails = () -> {
            count.incrementAndGet();
            throw new RuntimeException("fail");
        };
        var strategy = new TimeLimitedFixedWaitRetryStrategy(config);
        assertThrows(Exception.class, () -> strategy.execute(alwaysFails));
        assertTrue(count.get() > 3, "Expected >3 attempts (withMaxRetries(-1) disabled default cap), got " + count.get());
    }

    @Test
    void nonMatchingExceptionNotRetried() {
        var config = CountLimitedFixedWaitRetryConfig.builder()
                .maxAttempts(3)
                .waitTime(Duration.milliseconds(1))
                .retriableExceptions(Set.of("IOException"))
                .build();
        AtomicInteger count = new AtomicInteger(0);
        Callable<Boolean> alwaysFails = () -> {
            count.incrementAndGet();
            throw new RuntimeException("fail");
        };
        var strategy = new CountLimitedFixedWaitRetryStrategy(config);
        assertThrows(Exception.class, () -> strategy.execute(alwaysFails));
        assertEquals(1, count.get());
    }

    @Test
    void matchingExceptionRetried() throws Exception {
        var config = CountLimitedFixedWaitRetryConfig.builder()
                .maxAttempts(3)
                .waitTime(Duration.milliseconds(1))
                .retriableExceptions(Set.of("IOException"))
                .build();
        AtomicInteger count = new AtomicInteger(0);
        Callable<Boolean> alwaysFails = () -> {
            count.incrementAndGet();
            throw new java.io.IOException("fail");
        };
        var strategy = new CountLimitedFixedWaitRetryStrategy(config);
        assertThrows(Exception.class, () -> strategy.execute(alwaysFails));
        assertEquals(3, count.get());
    }

    @Test
    void falseReturnIsSuccessNotRetried() throws Exception {
        // PAR-09: Boolean return is a success signal. A Callable returning false
        // must NOT trigger a retry (no handleResult(false) in any policy).
        var config = CountLimitedFixedWaitRetryConfig.builder()
                .maxAttempts(3)
                .waitTime(Duration.milliseconds(1))
                .retriableExceptions(null)
                .build();
        AtomicInteger count = new AtomicInteger(0);
        Callable<Boolean> returnsFalse = () -> {
            count.incrementAndGet();
            return false;
        };
        var strategy = new CountLimitedFixedWaitRetryStrategy(config);
        boolean result = strategy.execute(returnsFalse);
        assertEquals(1, count.get(), "false return must be treated as success, not retried");
        assertFalse(result);
    }
}
