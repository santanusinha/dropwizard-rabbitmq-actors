package io.appform.dropwizard.actors.observers;

import lombok.Getter;

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

    public abstract <T> T executePublish(final PublishObserverContext context, final Supplier<T> supplier);

    public abstract <T> T executeConsume(final ConsumeObserverContext context, final Supplier<T> supplier);

    public final RMQObserver setNext(final RMQObserver next) {
        this.next = next;
        return this;
    }

    protected final <T> T proceedPublish(final PublishObserverContext context,
                                         final Supplier<T> supplier) {
        if (null == next) {
            return supplier.get();
        }
        return next.executePublish(context, supplier);
    }

    protected final <T> T proceedConsume(final ConsumeObserverContext context, final Supplier<T> supplier) {
        if (null == next) {
            return supplier.get();
        }
        return next.executeConsume(context, supplier);
    }
}
