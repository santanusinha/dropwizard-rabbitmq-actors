package io.appform.dropwizard.actors.observers;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.actor.MessageMetadata;

import java.util.function.Function;

/**
 *
 */
public final class TerminalRMQObserver extends RMQObserver {
    public TerminalRMQObserver() {
        super(null);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, Function<AMQP.BasicProperties, T> supplier) {
        return proceedPublish(context, supplier);
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, Function<MessageMetadata, T> supplier) {
        return proceedConsume(context, supplier);
    }
}
