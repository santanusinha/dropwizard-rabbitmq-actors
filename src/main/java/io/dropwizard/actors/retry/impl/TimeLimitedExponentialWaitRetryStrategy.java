package io.dropwizard.actors.retry.impl;

import com.github.rholder.retry.*;
import io.dropwizard.actors.retry.RetryStrategy;
import io.dropwizard.actors.retry.config.TimeLimitedExponentialWaitRetryConfig;
import io.dropwizard.actors.utils.CommonUtils;

import java.util.concurrent.TimeUnit;

/**
 * Limits retry time
 */
public class TimeLimitedExponentialWaitRetryStrategy extends RetryStrategy {
    public TimeLimitedExponentialWaitRetryStrategy(TimeLimitedExponentialWaitRetryConfig config) {
        super(RetryerBuilder.<Boolean>newBuilder()
                .retryIfException(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(),
                        exception))
                .withStopStrategy(
                        StopStrategies.stopAfterDelay(config.getMaxTime().toMilliseconds(), TimeUnit.MILLISECONDS))
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                .withWaitStrategy(
                        WaitStrategies.exponentialWait(config.getMultipier(),
                                config.getMaxTimeBetweenRetries().toMilliseconds(), TimeUnit.MILLISECONDS))
                .build());
    }
}
