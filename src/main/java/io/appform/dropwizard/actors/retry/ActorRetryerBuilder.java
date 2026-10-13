package io.appform.dropwizard.actors.retry;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.StopStrategy;
import com.github.rholder.retry.WaitStrategies;
import com.github.rholder.retry.WaitStrategy;
import com.google.common.base.Predicate;

import java.util.Objects;

/**
 * Defines the policy shared by synchronous and broker-scheduled retries.
 */
public final class ActorRetryerBuilder<V> {

    private Predicate<Attempt<V>> rejectionPredicate = attempt -> false;
    private StopStrategy stopStrategy;
    private WaitStrategy waitStrategy;

    private ActorRetryerBuilder() {
    }

    public static <V> ActorRetryerBuilder<V> newBuilder() {
        return new ActorRetryerBuilder<>();
    }

    public ActorRetryerBuilder<V> retryIfException(Predicate<Throwable> predicate) {
        Objects.requireNonNull(predicate, "exceptionPredicate");
        var previous = rejectionPredicate;
        rejectionPredicate = attempt -> previous.apply(attempt)
                || (attempt.hasException() && predicate.apply(attempt.getExceptionCause()));
        return this;
    }

    public ActorRetryerBuilder<V> withStopStrategy(StopStrategy strategy) {
        Objects.requireNonNull(strategy, "stopStrategy");
        if (stopStrategy != null) {
            throw new IllegalStateException("A stop strategy has already been set");
        }
        stopStrategy = strategy;
        return this;
    }

    public ActorRetryerBuilder<V> withWaitStrategy(WaitStrategy strategy) {
        Objects.requireNonNull(strategy, "waitStrategy");
        if (waitStrategy != null) {
            throw new IllegalStateException("A wait strategy has already been set");
        }
        waitStrategy = strategy;
        return this;
    }

    public Policy<V> build() {
        return new Policy<>(rejectionPredicate,
                stopStrategy == null ? StopStrategies.neverStop() : stopStrategy,
                waitStrategy == null ? WaitStrategies.noWait() : waitStrategy);
    }

    public record Policy<V>(Predicate<Attempt<V>> rejectionPredicate,
                            StopStrategy stopStrategy,
                            WaitStrategy waitStrategy) {

        public Policy {
            Objects.requireNonNull(rejectionPredicate, "rejectionPredicate");
            Objects.requireNonNull(stopStrategy, "stopStrategy");
            Objects.requireNonNull(waitStrategy, "waitStrategy");
        }

        public Retryer<V> buildSyncRetryer() {
            return new Retryer<>(stopStrategy, waitStrategy, rejectionPredicate);
        }
    }
}
