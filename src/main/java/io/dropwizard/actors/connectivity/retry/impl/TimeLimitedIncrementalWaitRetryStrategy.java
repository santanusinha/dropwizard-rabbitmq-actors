package io.dropwizard.actors.connectivity.retry.impl;

import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import io.dropwizard.actors.connectivity.retry.RetryStrategy;
import io.dropwizard.actors.connectivity.retry.config.TimeLimitedIncrementalWaitRetryConfig;

import java.util.concurrent.TimeUnit;

/**
 * Limits retry time
 */
public class TimeLimitedIncrementalWaitRetryStrategy extends RetryStrategy {
    public TimeLimitedIncrementalWaitRetryStrategy(TimeLimitedIncrementalWaitRetryConfig config) {
        super(RetryerBuilder.<Boolean>newBuilder()
                .retryIfException()
                .withStopStrategy(
                        StopStrategies.stopAfterDelay(config.getMaxTime().toMilliseconds(), TimeUnit.MILLISECONDS))
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                .withWaitStrategy(
                        WaitStrategies.incrementingWait(config.getInitialWaitTime().toMilliseconds(), TimeUnit.MILLISECONDS,
                                config.getWaitIncrement().toMilliseconds(), TimeUnit.MILLISECONDS))
                .build());
    }
}
