package io.appform.dropwizard.actors.actor;

import com.rabbitmq.client.AMQP;
import io.dropwizard.lifecycle.Managed;

/**
 * This interface is used to implement any actor to produce & consume messages.
 *
 * @param <Message>
 */
@SuppressWarnings({"java:S112", "java:S119"})
public interface IBaseActor<Message> extends Managed {

    /**
     * <p>This method is used to publish message with provided delay in milliseconds</p>
     *
     * @param message data to be published in queue
     * @param delayMilliseconds param to provide delay value
     * @throws Exception
     */
    void publishWithDelay(final Message message,
                          final long delayMilliseconds) throws Exception;

    /**
     * <p>This method is used to publish message with provided expiry in milliseconds, message will be auto-expired post expiryMs crosses</p>
     *
     * @param message data to be published in queue
     * @param expiryInMs param to provide expiration time of message
     * @throws Exception
     */
    void publishWithExpiry(final Message message,
                           final long expiryInMs) throws Exception;

    /**
     * <p>This method is used to publish message in queue</p>
     *
     * @param message data to be published in queue
     * @throws Exception
     */
    void publish(final Message message) throws Exception;

    /**
     * <p>This method is used to publish message in queue with additional properties of AMQP</p>
     *
     * @param message data to be published in queue
     * @param properties map of amqp properties
     * @throws Exception
     */
    void publish(final Message message,
                 final AMQP.BasicProperties properties) throws Exception;

    /**
     * <p>This method provides count of pending messages in queue</p>
     *
     * @return count of message pending in main queue
     */
    long pendingMessagesCount();

    /**
     * <p>This method provides count of pending messages in sidelined queue</p>
     *
     * @return count of message pending in sidelined queue
     */
    long pendingSidelineMessagesCount();
}
