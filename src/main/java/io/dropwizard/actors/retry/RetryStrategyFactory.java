package io.dropwizard.actors.retry;

import io.dropwizard.actors.retry.config.*;
import io.dropwizard.actors.retry.impl.*;

/**
 * Creates strategy based on config
 */
public class RetryStrategyFactory {
    public RetryStrategy create(RetryConfig config) {
        switch (config.getType()) {
            case NO_RETRY:
                return new NoRetryStrategy(NoRetryConfig.class.cast(config));
            case TIME_LIMITED_EXPONENTIAL_BACKOFF:
                return new TimeLimitedExponentialWaitRetryStrategy(TimeLimitedExponentialWaitRetryConfig.class.cast(config));
            case TIME_LIMITED_INCREMENTAL_WAIT:
                return new TimeLimitedIncrementalWaitRetryStrategy(TimeLimitedIncrementalWaitRetryConfig.class.cast(config));
            case TIME_LIMITED_FIXED_WAIT:
                return new TimeLimitedFixedWaitRetryStrategy(TimeLimitedFixedWaitRetryConfig.class.cast(config));
            case COUNT_LIMITED_EXPONENTIAL_BACKOFF:
                return new CountLimitedExponentialWaitRetryStrategy(CountLimitedExponentialWaitRetryConfig.class.cast(config));
            case COUNT_LIMITED_INCREMENTAL_WAIT:
                return new CountLimitedIncrementalWaitRetryStrategy(CountLimitedIncrementalWaitRetryConfig.class.cast(config));
            case COUNT_LIMITED_FIXED_WAIT:
                return new CountLimitedFixedWaitRetryStrategy(CountLimitedFixedWaitRetryConfig.class.cast(config));
        }
        return null;
    }
}
