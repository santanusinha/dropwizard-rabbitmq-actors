package io.dropwizard.actors.retry.impl;

import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import io.dropwizard.actors.retry.RetryStrategy;
import io.dropwizard.actors.retry.config.NoRetryConfig;

/**
 * No retries
 */
public class NoRetryStrategy extends RetryStrategy {
    @SuppressWarnings("unused")
    public NoRetryStrategy(NoRetryConfig config) {
        super(RetryerBuilder.<Boolean>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterAttempt(1))
                .build());
    }
}
