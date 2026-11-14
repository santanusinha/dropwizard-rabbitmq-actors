package io.appform.dropwizard.actors.messaging;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import java.time.Duration;

/**
 * Per-delivery context handed to a {@link QueueHandler}.
 */
public interface MessageContext<M> {

    /**
     * Logical queue name, as configured.
     */
    String queue();

    MessageMetadata metadata();

    /**
     * Redelivers {@code message} to this queue only, after {@code delay}. The message goes through the queue's private
     * retry exchange, so the other queues bound to the same exchange never see it. Requires
     * {@code retryLaterEnabled: true} on the queue.
     *
     * <p>Call it as the last step and then {@code return true}: the current delivery is still acked/rejected based on
     * the handler's outcome, so calling it and then throwing (the handler is retried and schedules again) or returning
     * false (the original is sidelined too) produces duplicates. Handlers should be idempotent.
     */
    void retryLater(M message, Duration delay);
}
