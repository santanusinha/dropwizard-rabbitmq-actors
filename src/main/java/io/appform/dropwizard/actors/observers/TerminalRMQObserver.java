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
    public <T> T executePublish(PublishObserverContext context, Function<PublishMessageDetails, T> publishFunction) {
        return proceedPublish(context, publishFunction);
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, Function<ConsumeMessageDetails, T> consumeFunction) {
        return proceedConsume(context, consumeFunction);
    }
}
