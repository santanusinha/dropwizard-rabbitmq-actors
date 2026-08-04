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

    /**
     * Wraps the given {@link WaitStrategy} so that a bounded random jitter (in the range
     * [-jitterFactor, +jitterFactor] of the computed wait) is applied around the wait time.
     *
     * @param waitStrategy the delegate wait strategy whose wait time will be jittered
     * @param jitterFactor fraction (0.0 to 1.0) of the computed wait used as the jitter bound
     * @return a bounded-jitter-decorated wait strategy
     */
    public static WaitStrategy withJitter(final WaitStrategy waitStrategy, final double jitterFactor) {
        return new BoundedJitterWaitStrategy(waitStrategy, jitterFactor);
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

        private BoundedJitterWaitStrategy(final WaitStrategy delegate, final double jitterFactor) {
            Preconditions.checkNotNull(delegate, "delegate wait strategy should not be null");
            Preconditions.checkArgument(jitterFactor >= 0.0 && jitterFactor <= 1.0,
                    "jitterFactor must be between 0.0 and 1.0");
            this.delegate = delegate;
            this.jitterFactor = jitterFactor;
        }

        @Override
        public long computeSleepTime(final Attempt failedAttempt) {
            final long baseSleepTime = delegate.computeSleepTime(failedAttempt);

            if (jitterFactor <= 0.0 || baseSleepTime <= 0) {
                return baseSleepTime;
            }

            // range: [-jitterFactor, +jitterFactor]
            final double randomFactor = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitterFactor;
            return (long) (baseSleepTime * (1 + randomFactor)); //  [baseSleepTime - jitterFactor, baseSleepTime + jitterFactor]
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
