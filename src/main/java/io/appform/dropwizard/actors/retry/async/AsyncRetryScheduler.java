package io.appform.dropwizard.actors.retry.async;

import com.rabbitmq.client.AMQP;

public interface AsyncRetryScheduler {

    <T> void schedule(T body, AMQP.BasicProperties properties) throws Exception;

}
