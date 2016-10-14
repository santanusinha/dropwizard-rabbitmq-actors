package io.dropwizard.actors.connectivity.retry.config;

import io.dropwizard.actors.connectivity.retry.RetryType;

/**
 * No retry will be done
 */
public class NoRetryConfig extends RetryConfig {
    public NoRetryConfig() {
        super(RetryType.NO_RETRY);
    }
}
