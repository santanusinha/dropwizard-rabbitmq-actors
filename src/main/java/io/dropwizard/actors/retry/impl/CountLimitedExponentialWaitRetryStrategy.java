package io.dropwizard.actors.retry.impl;

import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import io.dropwizard.actors.retry.RetryStrategy;
import io.dropwizard.actors.retry.config.CountLimitedExponentialWaitRetryConfig;
import io.dropwizard.actors.utils.CommonUtils;

import java.util.concurrent.TimeUnit;

/**
 * Limits retry time
 */
public class CountLimitedExponentialWaitRetryStrategy extends RetryStrategy {
    public CountLimitedExponentialWaitRetryStrategy(CountLimitedExponentialWaitRetryConfig config) {
        super(RetryerBuilder.<Boolean>newBuilder()
                .retryIfException(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(),
                        exception))
                .withStopStrategy(StopStrategies.stopAfterAttempt(config.getMaxAttempts()))
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                .withWaitStrategy(
                        WaitStrategies.exponentialWait(config.getMultipier(),
                                config.getMaxTimeBetweenRetries().toMilliseconds(), TimeUnit.MILLISECONDS))
                .build());
    }
}
