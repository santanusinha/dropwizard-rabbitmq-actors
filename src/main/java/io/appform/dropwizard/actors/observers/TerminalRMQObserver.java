package io.appform.dropwizard.actors.observers;

import java.util.function.Function;

/**
 *
 */
public final class TerminalRMQObserver extends RMQObserver {
    public TerminalRMQObserver() {
        super(null);
    }

    @Override
    public <T, R> R executePublish(PublishObserverContext context, Function<PublishObserverContext, R> function) {
        return proceedPublish(context, function);
    }

    @Override
    public <T, R> R executeConsume(ConsumeObserverContext context, Function<ConsumeObserverContext, R> function) {
        return proceedConsume(context, function);
    }
}
