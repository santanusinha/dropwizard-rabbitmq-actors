package io.dropwizard.actors.connectivity.retry.impl;

import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import io.dropwizard.actors.connectivity.retry.RetryStrategy;
import io.dropwizard.actors.connectivity.retry.config.CountLimitedFixedWaitRetryConfig;

import java.util.concurrent.TimeUnit;

/**
 * Limits retry time
 */
public class CountLimitedFixedWaitRetryStrategy extends RetryStrategy {
    public CountLimitedFixedWaitRetryStrategy(CountLimitedFixedWaitRetryConfig config) {
        super(RetryerBuilder.<Boolean>newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(config.getMaxAttempts()))
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                .withWaitStrategy(
                        WaitStrategies.fixedWait(config.getWaitTime().toMilliseconds(), TimeUnit.MILLISECONDS))
                .build());
    }
}
