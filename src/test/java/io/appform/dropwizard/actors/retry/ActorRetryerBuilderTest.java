package io.appform.dropwizard.actors.retry;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActorRetryerBuilderTest {

    @Test
    void defaultsDoNotRetryEvenExceptions() {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder().build();
        var failure = failure(new IOException());

        assertFalse(policy.rejectionPredicate().apply(failure));
        assertFalse(policy.stopStrategy().shouldStop(failure));
        assertEquals(0, policy.waitStrategy().computeSleepTime(failure));
        assertThrows(ExecutionException.class,
                () -> policy.buildSyncRetryer().call(() -> { throw new IOException(); }));
    }

    @Test
    void exceptionPredicatesAreCombinedWithOrAndDoNotRejectResults() {
        var policy = ActorRetryerBuilder.<Boolean>newBuilder()
                .retryIfException(IOException.class::isInstance)
                .retryIfException(IllegalStateException.class::isInstance)
                .build();

        assertTrue(policy.rejectionPredicate().apply(failure(new IOException())));
        assertTrue(policy.rejectionPredicate().apply(failure(new IllegalStateException())));
        assertFalse(policy.rejectionPredicate().apply(failure(new IllegalArgumentException())));
        var result = failure(null);
        when(result.hasException()).thenReturn(false);
        assertFalse(policy.rejectionPredicate().apply(result));
        verify(result, never()).getExceptionCause();
    }

    @Test
    void policyRetainsStrategiesAndDoesNotChangeWhenBuilderChanges() {
        var stop = StopStrategies.stopAfterAttempt(3);
        var wait = WaitStrategies.incrementingWait(10, TimeUnit.MILLISECONDS, 5, TimeUnit.MILLISECONDS);
        var builder = ActorRetryerBuilder.<Boolean>newBuilder()
                .withStopStrategy(stop)
                .withWaitStrategy(wait);
        var policy = builder.build();
        builder.retryIfException(error -> true);
        var attempt = failure(new IOException());
        when(attempt.getAttemptNumber()).thenReturn(2L);

        assertSame(stop, policy.stopStrategy());
        assertSame(wait, policy.waitStrategy());
        assertFalse(policy.rejectionPredicate().apply(attempt));
        assertTrue(builder.build().rejectionPredicate().apply(attempt));
        assertFalse(policy.stopStrategy().shouldStop(attempt));
        assertEquals(15, policy.waitStrategy().computeSleepTime(attempt));
        when(attempt.getAttemptNumber()).thenReturn(3L);
        assertTrue(policy.stopStrategy().shouldStop(attempt));
    }

    @Test
    void duplicateStrategiesAndNullInputsAreRejected() {
        var builder = ActorRetryerBuilder.<Boolean>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterAttempt(1))
                .withWaitStrategy(WaitStrategies.noWait());

        var stopStrategy = StopStrategies.neverStop();
        var waitStrategy = WaitStrategies.noWait();
        assertThrows(IllegalStateException.class,
                () -> builder.withStopStrategy(stopStrategy));
        assertThrows(IllegalStateException.class,
                () -> builder.withWaitStrategy(waitStrategy));
        assertThrows(NullPointerException.class, () -> builder.retryIfException(null));
        assertThrows(NullPointerException.class, () -> builder.withStopStrategy(null));
        assertThrows(NullPointerException.class, () -> builder.withWaitStrategy(null));
    }

    @SuppressWarnings("unchecked")
    private static Attempt<Boolean> failure(Throwable cause) {
        Attempt<Boolean> attempt = mock(Attempt.class);
        when(attempt.hasException()).thenReturn(true);
        if (cause != null) {
            when(attempt.getExceptionCause()).thenReturn(cause);
        }
        return attempt;
    }
}
