package io.appform.dropwizard.actors.retry.waitstrategies;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.WaitStrategy;
import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Factory for wait strategy decorators.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WaitStrategyFactory {

    public static WaitStrategy withBoundedJitter(final WaitStrategy waitStrategy, final double jitterFactor) {
        return new BoundedJitterWaitStrategy(waitStrategy, jitterFactor, Long.MAX_VALUE);
    }

    public static WaitStrategy withBoundedJitter(final WaitStrategy waitStrategy, final double jitterFactor, final long maxWaitMillis) {
        return new BoundedJitterWaitStrategy(waitStrategy, jitterFactor, maxWaitMillis);
    }

    /**
     * Wraps the given {@link WaitStrategy} so that the wait time is replaced with a random value
     * uniformly distributed in [0, baseWait] (AWS "Full Jitter").
     *
     * @param waitStrategy the delegate wait strategy whose wait time will be jittered
     * @return a full-jitter-decorated wait strategy
     */
    public static WaitStrategy withFullJitter(final WaitStrategy waitStrategy) {
        return new FullJitterWaitStrategy(waitStrategy);
    }

    /**
     * A {@link WaitStrategy} decorator that delegates the base wait computation and adds a bounded
     * random jitter around it.
     */
    private static final class BoundedJitterWaitStrategy implements WaitStrategy {

        private final WaitStrategy delegate;
        private final double jitterFactor;
        private final long maxWaitMillis;

        private BoundedJitterWaitStrategy(final WaitStrategy delegate, final double jitterFactor, final long maxWaitMillis) {
            Preconditions.checkNotNull(delegate, "delegate wait strategy should not be null");
            Preconditions.checkArgument(jitterFactor >= 0.0 && jitterFactor <= 1.0,
                    "jitterFactor must be between 0.0 and 1.0");
            this.delegate = delegate;
            this.jitterFactor = jitterFactor;
            this.maxWaitMillis = maxWaitMillis;
        }

        @Override
        public long computeSleepTime(final Attempt failedAttempt) {
            final long baseSleepTime = delegate.computeSleepTime(failedAttempt);

            if (jitterFactor <= 0.0 || baseSleepTime <= 0) {
                return baseSleepTime;
            }

            // range: [-jitterFactor, +jitterFactor]
            final double randomFactor = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitterFactor;
            long result = (long) (baseSleepTime * (1 + randomFactor));

            if (result > this.maxWaitMillis) {
                result = this.maxWaitMillis;
            }

            return Math.max(result, 0L);
        }
    }

    /**
     * A {@link WaitStrategy} decorator that replaces the delegate's wait time with a random value
     * uniformly distributed in [0, baseWait].
     */
    private static final class FullJitterWaitStrategy implements WaitStrategy {

        private final WaitStrategy delegate;

        private FullJitterWaitStrategy(final WaitStrategy delegate) {
            Preconditions.checkNotNull(delegate, "delegate wait strategy should not be null");
            this.delegate = delegate;
        }

        @Override
        public long computeSleepTime(final Attempt failedAttempt) {
            final long baseSleepTime = delegate.computeSleepTime(failedAttempt);

            if (baseSleepTime <= 0) {
                return baseSleepTime;
            }

            return ThreadLocalRandom.current().nextLong(baseSleepTime + 1); // [0, baseSleepTime]
        }
    }
}
