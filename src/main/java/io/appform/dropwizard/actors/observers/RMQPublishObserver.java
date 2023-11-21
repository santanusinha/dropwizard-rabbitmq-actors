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

    public abstract <T> T execute(final PublishObserverContext context, Supplier<T> supplier);

    public final RMQPublishObserver setNext(final RMQPublishObserver next) {
        this.next = next;
        return this;
    }

    protected final <T> T proceed(final PublishObserverContext context, final Supplier<T> supplier) {
        if (null == next) {
            return supplier.get();
        }
        return next.execute(context, supplier);
    }
}
