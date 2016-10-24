package io.dropwizard.actors.retry.config;

import io.dropwizard.actors.retry.RetryType;

/**
 * No retry will be done
 */
public class NoRetryConfig extends RetryConfig {
    public NoRetryConfig() {
        super(RetryType.NO_RETRY);
    }
}
