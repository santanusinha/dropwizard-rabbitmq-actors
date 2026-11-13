package io.appform.dropwizard.actors.retry.async;

import java.io.IOException;

/**
 * Signals that an async retry could not be handed off to the broker.
 * The consumer requeues the original delivery instead of applying the message-processing
 * exception policy, unless the error check marks the failure as ignorable.
 */
public class RetrySchedulingException extends IOException {

    public RetrySchedulingException(String message, Throwable cause) {
        super(message, cause);
    }
}
