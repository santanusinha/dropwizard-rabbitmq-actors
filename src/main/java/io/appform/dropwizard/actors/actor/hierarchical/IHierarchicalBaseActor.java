package io.appform.dropwizard.actors.actor.hierarchical;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.actor.IBaseActor;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.HierarchicalRoutingKey;

/**
 * This interface is used to implement any actor which have support of hierarchical message processing. RoutingKey param will drive queue selection from hierarchy
 *
 * @param <Message>
 */
@SuppressWarnings({"java:S112", "java:S119"})
public interface IHierarchicalBaseActor<Message> extends IBaseActor<Message> {

    /**
     * <p>This method is used to publish message with provided delay in milliseconds on queue matching to provided routingKey</p>
     *
     * @param routingKey param used to select queue from hierarchy
     * @param message data to be published in queue
     * @param delayMilliseconds param to provide delay value
     * @throws Exception
     */
    void publishWithDelay(final HierarchicalRoutingKey<String> routingKey,
                          final Message message,
                          final long delayMilliseconds) throws Exception;

    /**
     * <p>This method is used to publish message with provided expiry in milliseconds,
     * message will be auto-expired post expiryMs crosses on queue matching to provided routingKey</p>
     *
     * @param routingKey param used to select queue from hierarchy
     * @param message data to be published in queue
     * @param expiryInMs param to provide expiration time of message
     * @throws Exception
     */
    void publishWithExpiry(final HierarchicalRoutingKey<String> routingKey,
                           final Message message,
                           final long expiryInMs) throws Exception;

    /**
     * <p>This method is used to publish message in queue</p>
     *
     * @param routingKey param used to select queue from hierarchy on queue matching to provided routingKey
     * @param message data to be published in queue
     * @throws Exception
     */
    void publish(final HierarchicalRoutingKey<String> routingKey,
                 final Message message) throws Exception;

    /**
     * <p>This method is used to publish message in queue with additional properties of AMQP on queue matching to provided routingKey</p>
     *
     * @param routingKey param used to select queue from hierarchy
     * @param message data to be published in queue
     * @param properties map of amqp properties
     * @throws Exception
     */
    void publish(final HierarchicalRoutingKey<String> routingKey,
                 final Message message,
                 final AMQP.BasicProperties properties) throws Exception;

    /**
     * <p>This method provides count of pending messages in queue matching to provided routingKey</p>
     *
     * @param routingKey param used to select queue from hierarchy
     * @return count of message pending in main queue
     */
    long pendingMessagesCount(final HierarchicalRoutingKey<String> routingKey);

    /**
     * <p>This method provides count of pending messages in sidelined queue matching to provided routingKey</p>
     *
     * @param routingKey param used to select queue from hierarchy
     * @return count of message pending in sidelined queue
     */
    long pendingSidelineMessagesCount(final HierarchicalRoutingKey<String> routingKey);
}
