package io.appform.dropwizard.actors.retry.async;

/**
 * A permanent scheduling rejection that follows the consumer's processing-error policy.
 */
public class RetrySchedulingRejectedException extends Exception {

    public RetrySchedulingRejectedException(String message) {
        super(message);
    }

    public RetrySchedulingRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
