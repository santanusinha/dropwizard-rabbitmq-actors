package io.appform.dropwizard.actors.observers;

import java.util.function.Supplier;

/**
 *
 */
public final class TerminalObserver extends RMQPublishObserver {
    public TerminalObserver() {
        super(null);
    }

    @Override
    public <T> T execute(PublishObserverContext context, Supplier<T> supplier) {
        return proceed(context, supplier);
    }
}
