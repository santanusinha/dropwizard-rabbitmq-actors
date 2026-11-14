package io.appform.dropwizard.actors.retry.async;

import com.github.rholder.retry.Attempt;

import java.util.concurrent.ExecutionException;

record DeliveryAttempt(
        Boolean result,
        Throwable exception,
        long attemptNumber,
        long delaySinceFirstAttempt
) implements Attempt<Boolean> {

    @Override
    public Boolean get() throws ExecutionException {
        if (hasException()) {
            throw new ExecutionException(exception);
        }
        return result;
    }

    @Override
    public boolean hasResult() {
        return !hasException();
    }

    @Override
    public boolean hasException() {
        return exception != null;
    }

    @Override
    public Boolean getResult() {
        if (!hasResult()) {
            throw new IllegalStateException("The attempt failed with an exception");
        }
        return result;
    }

    @Override
    public Throwable getExceptionCause() {
        if (!hasException()) {
            throw new IllegalStateException("The attempt returned a result");
        }
        return exception;
    }

    @Override
    public long getAttemptNumber() {
        return attemptNumber;
    }

    @Override
    public long getDelaySinceFirstAttempt() {
        return delaySinceFirstAttempt;
    }
}
