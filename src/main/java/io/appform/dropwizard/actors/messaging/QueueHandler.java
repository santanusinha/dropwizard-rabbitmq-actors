package io.appform.dropwizard.actors.messaging;

import java.util.Set;

/**
 * Consumes messages from one queue. Handlers have no way to publish to the exchange they consume from; use
 * {@link MessageContext#retryLater} to redeliver a message to this queue only.
 */
@FunctionalInterface
public interface QueueHandler<M> {

    /**
     * @return true to ack the message; false to reject it (it is dead-lettered to the queue's sideline queue).
     *         Exceptions are retried according to the queue's {@code retryConfig}.
     */
    boolean handle(M message, MessageContext<M> context) throws Exception;

    /**
     * Called instead of {@link #handle} for messages published with an expiry that has passed. Acks by default.
     */
    default boolean handleExpired(final M message, final MessageContext<M> context) throws Exception {
        return true;
    }

    /**
     * Exceptions (and subtypes, anywhere in the cause chain) for which the message is acked and dropped instead of
     * rejected.
     */
    default Set<Class<? extends Throwable>> droppedExceptionTypes() {
        return Set.of();
    }
}
