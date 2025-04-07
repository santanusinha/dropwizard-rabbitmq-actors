package io.appform.dropwizard.actors.observers;

import java.util.function.Function;
import java.util.function.Supplier;

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
    public <T> T executeConsume(ConsumeObserverContext context, Supplier<T> supplier) {
        return proceedConsume(context, supplier);
    }
}
