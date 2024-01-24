package io.appform.dropwizard.actors.observers;

import java.util.function.Supplier;

/**
 *
 */
public final class TerminalRMQObserver extends RMQObserver {
    public TerminalRMQObserver() {
        super(null);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, Supplier<T> supplier) {
        return proceedPublish(context, supplier);
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, Supplier<T> supplier) {
        return proceedConsume(context, supplier);
    }
}
