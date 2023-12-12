package io.appform.dropwizard.actors.observers;

import java.util.function.Supplier;

/**
 *
 */
public final class TerminalObserver extends RMQObserver {
    public TerminalObserver() {
        super(null);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, Supplier<T> supplier) {
        return proceedPublish(context, supplier);
    }

    @Override
    public <T> T executeConsume(PublishObserverContext context, Supplier<T> supplier) {
        return proceedConsume(context, supplier);
    }
}
