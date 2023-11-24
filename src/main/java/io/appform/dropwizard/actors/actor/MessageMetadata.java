package io.appform.dropwizard.actors.actor;

import com.rabbitmq.client.AMQP;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class MessageMetadata {

    private boolean redelivered;
    private long delayInMs;
    private AMQP.BasicProperties properties;

}
