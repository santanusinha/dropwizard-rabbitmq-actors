package io.appform.dropwizard.actors.retry.async;

import com.rabbitmq.client.AMQP;
import java.util.Map;
import java.util.Objects;

public interface AsyncRetryScheduler {

    String RETRY_QUEUE_HEADER = "x-actor-retry-queue";
    String RETRY_ATTEMPT_HEADER = "x-actor-retry-attempt";
    String RETRY_STARTED_AT_HEADER = "x-actor-retry-started-at";

    /**
     * Completes the retry handoff before returning. Permanent rejections should throw
     * {@link RetrySchedulingRejectedException}. Transport failures should throw
     * {@link RetrySchedulingException}, on which the consumer requeues the original delivery.
     */
    void schedule(String queueName, byte[] body, AMQP.BasicProperties properties,
        RetryState nextState, long delayMs) throws Exception;

    /**
     * Restores the retry progress carried by a delivery, or an initial state
     * for a fresh delivery. Throws {@link IllegalStateException} on malformed
     * persisted metadata; the delivery then follows the consumer's processing-error
     * policy without running the handler.
     */
    default RetryState restoreState(String queueName, AMQP.BasicProperties properties, long now) {
        Objects.requireNonNull(queueName, "queueName");
        var headers = properties == null || properties.getHeaders() == null
            ? Map.<String, Object>of() : properties.getHeaders();
        var retryQueue = headers.get(RETRY_QUEUE_HEADER);
        var attemptHeader = headers.get(RETRY_ATTEMPT_HEADER);
        var startedAtHeader = headers.get(RETRY_STARTED_AT_HEADER);
        if (retryQueue == null && attemptHeader == null && startedAtHeader == null) {
            return new RetryState(1, now);
        }
        if (retryQueue == null) {
            throw malformedMetadata("Retry metadata is missing the queue header");
        }
        if (!queueName.equals(retryQueue.toString())) {
            return new RetryState(1, now);
        }
        var attemptCount = headerNumber(attemptHeader);
        var startedAt = headerNumber(startedAtHeader);
        if (attemptCount == null || attemptCount <= 0 || attemptCount >= Integer.MAX_VALUE) {
            throw malformedMetadata("Attempt count must be a positive integer below " + Integer.MAX_VALUE);
        }
        if (startedAt == null || startedAt < 0) {
            throw malformedMetadata("Started-at must be a nonnegative timestamp");
        }
        return new RetryState(attemptCount, startedAt);
    }

    default void start() throws Exception {
    }

    default void stop() throws Exception {
    }

    private static Long headerNumber(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private static IllegalStateException malformedMetadata(String detail) {
        return new IllegalStateException("Malformed actor retry metadata: " + detail);
    }

}
