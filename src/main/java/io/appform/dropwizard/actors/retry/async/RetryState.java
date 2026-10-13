package io.appform.dropwizard.actors.retry.async;

/**
 * Persistent retry progress restored from a redelivered message by the
 * {@link AsyncRetryScheduler} and handed back to it on the next handoff.
 */
public record RetryState(long attemptNumber, long startedAt) {

    public RetryState {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be positive");
        }
        if (startedAt < 0) {
            throw new IllegalArgumentException("Started-at must be nonnegative");
        }
    }
}
