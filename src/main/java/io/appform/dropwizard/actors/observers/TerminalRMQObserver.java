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
    public <T> T executePublish(final PublishObserverContext context, final Function<PublishObserverContext, T> function) {
        return proceedPublish(context, function);
    }

    @Override
    public <T> T executeConsume(final ConsumeObserverContext context, final Function<ConsumeObserverContext, T> function) {
        return proceedConsume(context, function);
    }
}
