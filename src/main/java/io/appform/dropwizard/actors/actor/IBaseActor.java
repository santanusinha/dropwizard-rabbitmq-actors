package io.appform.dropwizard.actors.actor;

import com.rabbitmq.client.AMQP;
import io.dropwizard.lifecycle.Managed;

public interface IBaseActor<Message> extends Managed {

    void publishWithDelay(final Message message,
                          final long delayMilliseconds) throws Exception;

    void publishWithExpiry(final Message message,
                           final long expiryInMs) throws Exception;

    void publish(final Message message) throws Exception;

    void publish(final Message message,
                 final AMQP.BasicProperties properties) throws Exception;

    long pendingMessagesCount();

    long pendingSidelineMessagesCount();
}
