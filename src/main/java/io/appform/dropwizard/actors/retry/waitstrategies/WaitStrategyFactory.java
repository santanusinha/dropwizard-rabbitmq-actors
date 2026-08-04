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
     * Wraps the given {@link WaitStrategy} so that a random jitter (bounded by {@code jitterFactor}
     * of the delegate's computed wait) is added on top of the delegate's wait time.
     *
     * @param waitStrategy the delegate wait strategy whose wait time will be jittered
     * @param jitterFactor fraction (0.0 to 1.0) of the computed wait used as the jitter upper bound
     * @return a jitter-decorated wait strategy
     */
    public static WaitStrategy withJitter(final WaitStrategy waitStrategy, final double jitterFactor) {
        return new JitterWaitStrategy(waitStrategy, jitterFactor);
    }

    /**
     * A {@link WaitStrategy} decorator that delegates the base wait computation and adds a bounded
     * random jitter on top of it.
     */
    private static final class JitterWaitStrategy implements WaitStrategy {

        private final WaitStrategy delegate;
        private final double jitterFactor;

        private JitterWaitStrategy(final WaitStrategy delegate, final double jitterFactor) {
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

            final long jitter = (long) (ThreadLocalRandom.current().nextLong() * jitterFactor * baseSleepTime);
            return baseSleepTime + jitter;
        }
    }
}

