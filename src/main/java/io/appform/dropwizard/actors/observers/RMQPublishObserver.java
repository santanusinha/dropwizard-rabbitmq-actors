package io.appform.dropwizard.actors.observers;

import lombok.Getter;

import java.util.function.Supplier;

/**
 *
 */
public abstract class RMQPublishObserver {

    @Getter
    private RMQPublishObserver next;

    protected RMQPublishObserver(RMQPublishObserver next) {
        this.next = next;
    }

    public abstract <T> T executePublish(final PublishObserverContext context, Supplier<T> supplier);

    public abstract <T> T executeConsume(final PublishObserverContext context, Supplier<T> supplier);

    public final RMQPublishObserver setNext(final RMQPublishObserver next) {
        this.next = next;
        return this;
    }

    protected final <T> T proceedPublish(final PublishObserverContext context, final Supplier<T> supplier) {
        if (null == next) {
            return supplier.get();
        }
        return next.executePublish(context, supplier);
    }

    protected final <T> T proceedConsume(final PublishObserverContext context, final Supplier<T> supplier) {
        if (null == next) {
            return supplier.get();
        }
        return next.executeConsume(context, supplier);
    }
}
