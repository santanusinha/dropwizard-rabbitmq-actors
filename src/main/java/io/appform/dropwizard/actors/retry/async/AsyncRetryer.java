package io.appform.dropwizard.actors.retry.async;

import com.github.rholder.retry.RetryException;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.retry.RetryPolicy;
import io.appform.dropwizard.actors.retry.WaitMode;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;

/**
 * Broker-scheduled retries: a failed attempt is handed off to an
 * {@link AsyncRetryScheduler} with its next {@link RetryState}; the scheduler
 * owns how that state is persisted across redeliveries.
 */
@Slf4j
public class AsyncRetryer implements ActorRetryer {

    private static final Throwable PLANNED_FAILURE = new IllegalStateException("Planned retry");

    private final Clock clock;
    private final RetryPolicy<Boolean> retryPolicy;
    private final AsyncRetryScheduler asyncRetryScheduler;

    public AsyncRetryer(RetryPolicy<Boolean> retryPolicy, AsyncRetryScheduler asyncRetryScheduler, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.asyncRetryScheduler = Objects.requireNonNull(asyncRetryScheduler, "asyncRetryScheduler");
    }

    public AsyncRetryer(RetryPolicy<Boolean> retryPolicy, AsyncRetryScheduler asyncRetryScheduler) {
        this(retryPolicy, asyncRetryScheduler, Clock.systemDefaultZone());
    }

    @Override
    public void start() throws Exception {
        asyncRetryScheduler.start();
    }

    @Override
    public void stop() throws Exception {
        asyncRetryScheduler.stop();
    }

    @Override
    public boolean call(Callable<Boolean> callable, String queueName, byte[] body,
                        AMQP.BasicProperties properties) throws Exception {
        Objects.requireNonNull(callable, "callable");
        Objects.requireNonNull(queueName, "queueName");
        var now = clock.millis();
        var state = asyncRetryScheduler.restoreState(queueName, properties, now);
        if (state.attemptNumber() > 1) {
            log.debug("Async retry redelivery: queue: {}, attempt: {}, messageId: {}, correlationId: {}",
                    queueName, state.attemptNumber(),
                    properties == null ? null : properties.getMessageId(),
                    properties == null ? null : properties.getCorrelationId());
        }
        var attempt = execute(callable, state);
        if (!retryPolicy.rejectionPredicate().apply(attempt)) {
            return attempt.get();
        }
        if (retryPolicy.stopStrategy().shouldStop(attempt)) {
            log.warn("Async retries exhausted: queue: {}, attempt: {}, elapsedMs: {}, messageId: {}, correlationId: {}",
                    queueName, attempt.getAttemptNumber(), attempt.getDelaySinceFirstAttempt(),
                    properties == null ? null : properties.getMessageId(),
                    properties == null ? null : properties.getCorrelationId());
            throw new RetryException((int) attempt.getAttemptNumber(), attempt);
        }
        var delayMs = retryPolicy.waitStrategy().computeSleepTime(attempt);
        var nextState = new RetryState(state.attemptNumber() + 1, state.startedAt());
        try {
            asyncRetryScheduler.schedule(queueName, body, properties, nextState, delayMs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RetrySchedulingException("Interrupted while scheduling retry for queue: " + queueName, error);
        }
        log.info("Async retry scheduled: queue: {}, failedAttempt: {}, nextAttempt: {}, delayMs: {}, "
                        + "messageId: {}, correlationId: {}",
                queueName, attempt.getAttemptNumber(), nextState.attemptNumber(), delayMs,
                properties == null ? null : properties.getMessageId(),
                properties == null ? null : properties.getCorrelationId());
        return true;
    }

    @Override
    public WaitMode getRetryMode() {
        return WaitMode.ASYNC;
    }

    private DeliveryAttempt execute(Callable<Boolean> callable, RetryState state) {
        DeliveryAttempt attempt;
        try {
            var result = callable.call();
            attempt = new DeliveryAttempt(result, null, state.attemptNumber(), elapsed(state.startedAt()));
        } catch (Throwable error) {
            attempt = new DeliveryAttempt(null, error, state.attemptNumber(), elapsed(state.startedAt()));
        }
        return attempt;
    }

    private long elapsed(long startedAt) {
        return Math.max(clock.millis() - startedAt, 0);
    }

    public static Set<Long> getPossibleDelays(RetryPolicy<Boolean> retryPolicy, int maxAttempts, boolean throwIfBreached) throws RetrySchedulingRejectedException {
        var delays = new TreeSet<Long>();
        var elapsed = 0L;
        for (long attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            var attempt = new DeliveryAttempt(null, PLANNED_FAILURE, attemptNumber, elapsed);
            if (retryPolicy.stopStrategy().shouldStop(attempt)) {
                return delays;
            }
            var delay = retryPolicy.waitStrategy().computeSleepTime(attempt);
            delays.add(delay);
            elapsed += delay;
        }
        if(throwIfBreached) {
            throw new RetrySchedulingRejectedException("Retry policy does not stop within " + maxAttempts + " planned attempts");
        } else {
            return delays;
        }
    }

}
