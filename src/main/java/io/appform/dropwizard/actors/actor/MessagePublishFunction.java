package io.appform.dropwizard.actors.actor;

import com.rabbitmq.client.AMQP;

@FunctionalInterface
public interface MessagePublishFunction<T> {
    T apply(AMQP.BasicProperties properties);
}
