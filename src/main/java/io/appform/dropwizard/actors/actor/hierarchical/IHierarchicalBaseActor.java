package io.appform.dropwizard.actors.actor.hierarchical;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.actor.IBaseActor;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.HierarchicalRoutingKey;

public interface IHierarchicalBaseActor<Message> extends IBaseActor<Message> {

    void publishWithDelay(final HierarchicalRoutingKey<String> routingKey,
                          final Message message,
                          final long delayMilliseconds) throws Exception;

    void publishWithExpiry(final HierarchicalRoutingKey<String> routingKey,
                           final Message message,
                           final long expiryInMs) throws Exception;

    void publish(final HierarchicalRoutingKey<String> routingKey,
                 final Message message) throws Exception;

    void publish(final HierarchicalRoutingKey<String> routingKey,
                 final Message message,
                 final AMQP.BasicProperties properties) throws Exception;

    long pendingMessagesCount(final HierarchicalRoutingKey<String> routingKey);

    long pendingSidelineMessagesCount(final HierarchicalRoutingKey<String> routingKey);
}
