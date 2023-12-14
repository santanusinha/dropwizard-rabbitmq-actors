package io.appform.dropwizard.actors.observers;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 */
public abstract class RMQObserver {

    @Getter
    private RMQObserver next;

    protected RMQObserver(RMQObserver next) {
        this.next = next;
    }

    public abstract <T> T executePublish(final PublishObserverContext context,
                                         final Function<HashMap<String, Object>, T> supplier,
                                         final HashMap<String, Object> headers);

    public abstract <T> T executeConsume(final PublishObserverContext context, Supplier<T> supplier,
                                         final Map<String, Object> headers);

    public final RMQObserver setNext(final RMQObserver next) {
        this.next = next;
        return this;
    }

    protected final <T> T proceedPublish(final PublishObserverContext context,
                                         final Function<HashMap<String, Object>, T> supplier,
                                         final HashMap<String, Object> headers) {
        if (null == next) {
            return supplier.apply(headers);
        }
        return next.executePublish(context, supplier, headers);
    }

    protected final <T> T proceedConsume(final PublishObserverContext context, final Supplier<T> supplier,
                                         final Map<String, Object> headers) {
        if (null == next) {
            return supplier.get();
        }
        return next.executeConsume(context, supplier, headers);
    }
}
