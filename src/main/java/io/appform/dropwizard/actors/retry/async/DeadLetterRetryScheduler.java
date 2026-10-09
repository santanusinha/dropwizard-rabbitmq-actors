package io.appform.dropwizard.actors.retry.async;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;

public class DeadLetterRetryScheduler implements AsyncRetryScheduler {

    UnmanagedPublisher<Object> publisher;

    @Override
    public <T> void schedule(T body, AMQP.BasicProperties properties) throws Exception {
        publisher.publish(body, properties);
    }

}
