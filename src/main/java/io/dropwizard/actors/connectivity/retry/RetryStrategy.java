package io.dropwizard.actors.connectivity.retry;

import com.github.rholder.retry.Retryer;

import java.util.concurrent.Callable;

/**
 * Baqse for all retry strategies
 */
public abstract class RetryStrategy {
    private final Retryer<Boolean> retryer;

    protected RetryStrategy(Retryer<Boolean> retryer) {
        this.retryer = retryer;
    }

    public boolean execute(Callable<Boolean> callable) throws Exception {
        return retryer.call(callable);
    }
}
