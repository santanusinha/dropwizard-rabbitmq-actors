package io.dropwizard.actors.connectivity.retry.impl;

import com.github.rholder.retry.*;
import io.dropwizard.actors.connectivity.retry.RetryStrategy;
import io.dropwizard.actors.connectivity.retry.config.TimeLimitedExponentialWaitRetryConfig;

import java.util.concurrent.TimeUnit;

/**
 * Limits retry time
 */
public class TimeLimitedExponentialWaitRetryStrategy extends RetryStrategy {
    public TimeLimitedExponentialWaitRetryStrategy(TimeLimitedExponentialWaitRetryConfig config) {
        super(RetryerBuilder.<Boolean>newBuilder()
                .retryIfException()
                .withStopStrategy(
                        StopStrategies.stopAfterDelay(config.getMaxTime().toMilliseconds(), TimeUnit.MILLISECONDS))
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                .withWaitStrategy(
                        WaitStrategies.exponentialWait(config.getMultipier(),
                                config.getMaxTimeBetweenRetries().toMilliseconds(), TimeUnit.MILLISECONDS))
                .build());
    }
}
