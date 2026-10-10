package io.appform.dropwizard.actors.retry.async;

import com.github.rholder.retry.RetryException;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.retry.ActorRetryerBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

/**
 * Broker-scheduled retries: a failed attempt is handed off to an
 * {@link AsyncRetryScheduler} with its next {@link RetryState}; the scheduler
 * owns how that state is persisted across redeliveries.
 */
@Slf4j
public class AsyncRetryer implements ActorRetryer {

    private final ActorRetryerBuilder.Policy<Boolean> retryPolicy;
    private final AsyncRetryScheduler asyncRetryScheduler;
    private final LongSupplier clock;

    public AsyncRetryer(ActorRetryerBuilder.Policy<Boolean> retryPolicy, AsyncRetryScheduler asyncRetryScheduler) {
        this(retryPolicy, asyncRetryScheduler, System::currentTimeMillis);
    }

    public AsyncRetryer(ActorRetryerBuilder.Policy<Boolean> retryPolicy,
                        AsyncRetryScheduler asyncRetryScheduler, LongSupplier clock) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.asyncRetryScheduler = Objects.requireNonNull(asyncRetryScheduler, "asyncRetryScheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
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
    public boolean call(Callable<Boolean> callable, String queueName, byte[] body, AMQP.BasicProperties properties) throws Exception {
        Objects.requireNonNull(callable, "callable");
        Objects.requireNonNull(queueName, "queueName");
        var now = clock.getAsLong();
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
        } catch (Exception error) {
            throw new RetrySchedulingException("Failed to schedule retry for queue: " + queueName, error);
        }
        log.info("Async retry scheduled: queue: {}, failedAttempt: {}, nextAttempt: {}, delayMs: {}, messageId: {}, correlationId: {}",
                queueName, attempt.getAttemptNumber(), nextState.attemptNumber(), delayMs,
                properties == null ? null : properties.getMessageId(),
                properties == null ? null : properties.getCorrelationId());
        return true;
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
        return Math.max(clock.getAsLong() - startedAt, 0);
    }
}
