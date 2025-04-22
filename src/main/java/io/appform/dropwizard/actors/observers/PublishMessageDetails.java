package io.appform.dropwizard.actors.observers;

import com.rabbitmq.client.AMQP;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublishMessageDetails {
    AMQP.BasicProperties messageProperties;
}
