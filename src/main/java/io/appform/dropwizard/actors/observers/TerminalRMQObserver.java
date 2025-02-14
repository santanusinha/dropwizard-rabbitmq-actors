package io.appform.dropwizard.actors.observers;

import io.appform.dropwizard.actors.actor.MessageConsumeFunction;
import io.appform.dropwizard.actors.actor.MessagePublishFunction;
/**
 *
 */
public final class TerminalRMQObserver extends RMQObserver {
    public TerminalRMQObserver() {
        super(null);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, MessagePublishFunction<T> supplier) {
        return proceedPublish(context, supplier);
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, MessageConsumeFunction<T> supplier) {
        return proceedConsume(context, supplier);
    }
}
