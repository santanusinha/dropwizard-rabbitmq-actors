package io.appform.dropwizard.actors.retry;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.StopStrategy;
import com.github.rholder.retry.WaitStrategy;
import com.google.common.base.Predicate;
import java.util.Objects;

public record RetryPolicy<V>(Predicate<Attempt<V>> rejectionPredicate,
                             StopStrategy stopStrategy,
                             WaitStrategy waitStrategy) {

  public RetryPolicy {
    Objects.requireNonNull(rejectionPredicate, "rejectionPredicate");
    Objects.requireNonNull(stopStrategy, "stopStrategy");
    Objects.requireNonNull(waitStrategy, "waitStrategy");
  }

  public Retryer<V> buildRetryer() {
    return new Retryer<>(stopStrategy, waitStrategy, rejectionPredicate);
  }
}
