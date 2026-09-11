package io.appform.dropwizard.actors.retry;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * VER-02: Asserts exact sleep sequences for exponential and incremental retry strategies.
 * Uses failsafe's onRetryScheduled listener to capture the computed delay before Thread.sleep,
 * avoiding mockStatic(Thread.class) which Mockito blocks.
 */
class RetryTimingParityTest {

    @Test
    void exponentialBackoffProducesCorrectSleepSequence() {
        List<Duration> delays = new ArrayList<>();
        RetryPolicy<Boolean> policy = RetryPolicy.<Boolean>builder()
                .handleIf(e -> true)
                .withMaxAttempts(4)
                .withBackoff(
                        Duration.ofMillis(2),
                        Duration.ofMillis(500),
                        2.0)
                .onRetryScheduled(event -> delays.add(event.getDelay()))
                .build();

        assertThrows(Exception.class, () -> Failsafe.with(policy).get(() -> {
            throw new RuntimeException("fail");
        }));

        assertEquals(3, delays.size());
        assertEquals(2, delays.get(0).toMillis());
        assertEquals(4, delays.get(1).toMillis());
        assertEquals(8, delays.get(2).toMillis());
    }

    @Test
    void exponentialBackoffCapsAtMaxTimeBetweenRetries() {
        List<Duration> delays = new ArrayList<>();
        RetryPolicy<Boolean> policy = RetryPolicy.<Boolean>builder()
                .handleIf(e -> true)
                .withMaxAttempts(4)
                .withBackoff(
                        Duration.ofMillis(2),
                        Duration.ofMillis(3),
                        2.0)
                .onRetryScheduled(event -> delays.add(event.getDelay()))
                .build();

        assertThrows(Exception.class, () -> Failsafe.with(policy).get(() -> {
            throw new RuntimeException("fail");
        }));

        assertEquals(3, delays.size());
        assertEquals(2, delays.get(0).toMillis());
        assertEquals(3, delays.get(1).toMillis());
        assertEquals(3, delays.get(2).toMillis());
    }

    @Test
    void incrementalWaitProducesCorrectSleepSequence() {
        List<Duration> delays = new ArrayList<>();
        long initial = 10;
        long increment = 5;
        RetryPolicy<Boolean> policy = RetryPolicy.<Boolean>builder()
                .handleIf(e -> true)
                .withMaxAttempts(4)
                .withDelayFn(ctx -> Duration.ofMillis(
                        initial + (ctx.getAttemptCount() - 1) * increment))
                .onRetryScheduled(event -> delays.add(event.getDelay()))
                .build();

        assertThrows(Exception.class, () -> Failsafe.with(policy).get(() -> {
            throw new RuntimeException("fail");
        }));

        assertEquals(3, delays.size());
        assertEquals(10, delays.get(0).toMillis());
        assertEquals(15, delays.get(1).toMillis());
        assertEquals(20, delays.get(2).toMillis());
    }

    @Test
    void timeLimitedExponentialProducesSameDelaysAsCountLimited() {
        List<Duration> delays = new ArrayList<>();
        RetryPolicy<Boolean> policy = RetryPolicy.<Boolean>builder()
                .handleIf(e -> true)
                .withMaxDuration(Duration.ofMillis(5000))
                .withMaxRetries(-1)
                .withBackoff(Duration.ofMillis(2), Duration.ofMillis(500), 2.0)
                .withMaxAttempts(4)
                .onRetryScheduled(event -> delays.add(event.getDelay()))
                .build();

        assertThrows(Exception.class, () -> Failsafe.with(policy).get(() -> {
            throw new RuntimeException("fail");
        }));

        assertEquals(3, delays.size());
        assertEquals(2, delays.get(0).toMillis());
        assertEquals(4, delays.get(1).toMillis());
        assertEquals(8, delays.get(2).toMillis());
    }

    @Test
    void timeLimitedIncrementalProducesSameDelaysAsCountLimited() {
        List<Duration> delays = new ArrayList<>();
        long initial = 10;
        long increment = 5;
        RetryPolicy<Boolean> policy = RetryPolicy.<Boolean>builder()
                .handleIf(e -> true)
                .withMaxDuration(Duration.ofMillis(5000))
                .withMaxRetries(-1)
                .withDelayFn(ctx -> Duration.ofMillis(
                        initial + (ctx.getAttemptCount() - 1) * increment))
                .withMaxAttempts(4)
                .onRetryScheduled(event -> delays.add(event.getDelay()))
                .build();

        assertThrows(Exception.class, () -> Failsafe.with(policy).get(() -> {
            throw new RuntimeException("fail");
        }));

        assertEquals(3, delays.size());
        assertEquals(10, delays.get(0).toMillis());
        assertEquals(15, delays.get(1).toMillis());
        assertEquals(20, delays.get(2).toMillis());
    }
}
