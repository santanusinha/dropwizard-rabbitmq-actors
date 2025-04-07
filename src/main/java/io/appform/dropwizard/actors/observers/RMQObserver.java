package io.appform.dropwizard.actors.observers;

import lombok.Getter;

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

    public abstract <T, R> R executePublish(final PublishObserverContext context, final Function<PublishObserverContext, R> function);

    public abstract <T> T executeConsume(final ConsumeObserverContext context, final Supplier<T> supplier);

    public final RMQObserver setNext(final RMQObserver next) {
        this.next = next;
        return this;
    }

    protected final <T, R> R proceedPublish(final PublishObserverContext context,
                                         final Function<PublishObserverContext, R> function) {
        if (null == next) {
            return function.apply(context);
        }
        return next.executePublish(context, function);
    }

    protected final <T> T proceedConsume(final ConsumeObserverContext context, final Supplier<T> supplier) {
        if (null == next) {
            return supplier.get();
        }
        return next.executeConsume(context, supplier);
    }
}
