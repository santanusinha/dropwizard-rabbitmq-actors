package io.appform.dropwizard.actors.observers;

import com.rabbitmq.client.AMQP.BasicProperties;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublishObserverContext {
    String queueName;
    BasicProperties properties;
}
