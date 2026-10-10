package io.appform.dropwizard.actors.retry.async;

import com.rabbitmq.client.AMQP;

public interface AsyncRetryScheduler {

    default void start() throws Exception {
    }

    default void stop() throws Exception {
    }

    /**
     * Restores the retry progress carried by a delivery, or an initial state
     * for a fresh delivery. Throws {@link IllegalStateException} on malformed
     * persisted metadata so the consumer rejects the delivery.
     */
    RetryState restoreState(String queueName, AMQP.BasicProperties properties, long now);

    void schedule(String queueName, byte[] body, AMQP.BasicProperties properties,
                  RetryState nextState, long delayMs) throws Exception;

}
