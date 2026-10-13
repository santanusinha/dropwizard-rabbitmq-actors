package io.appform.dropwizard.actors.retry;

/**
 * Decides how the retries configured by a {@link io.appform.dropwizard.actors.retry.config.RetryConfig} happen.
 */
public enum RetryMode {

    SYNC,

    ASYNC

}
