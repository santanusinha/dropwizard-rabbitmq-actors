package io.dropwizard.actors.connectivity.retry;

/**
 * Created by santanu on 14/10/16.
 */
public enum  RetryType {
    NO_RETRY,
    TIME_LIMITED_EXPONENTIAL_BACKOFF,
    TIME_LIMITED_INCREMENTAL_WAIT,
    TIME_LIMITED_FIXED_WAIT,
    COUNT_LIMITED_EXPONENTIAL_BACKOFF,
    COUNT_LIMITED_INCREMENTAL_WAIT,
    COUNT_LIMITED_FIXED_WAIT
}
