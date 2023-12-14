package io.appform.dropwizard.actors.observers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 */
public final class TerminalObserver extends RMQObserver {
    public TerminalObserver() {
        super(null);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, Function<HashMap<String, Object>, T> supplier, HashMap<String, Object> headers) {
        return proceedPublish(context, supplier, headers);
    }

    @Override
    public <T> T executeConsume(PublishObserverContext context, Supplier<T> supplier,
                                final Map<String, Object> headers) {
        return proceedConsume(context, supplier, headers);
    }
}
