package io.appform.dropwizard.actors.observers;

import lombok.extern.slf4j.Slf4j;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 *
 */
@Slf4j
public abstract class MetricObserver {

    private MetricObserver next;

    protected MetricObserver(MetricObserver next) {
        this.next = next;
    }

    public abstract <T> T execute(final ObserverContext context, Supplier<T> supplier);

    public final MetricObserver setNext(final MetricObserver next) {
        this.next = next;
        return this;
    }

    public final void visit(Consumer<MetricObserver> visitor) {
        visitor.accept(this);
        if (next != null) {
            next.visit(visitor);
        }
    }

    protected final <T> T proceed(final ObserverContext context, final Supplier<T> supplier) {
        if (null == next) {
            return supplier.get();
        }
        return next.execute(context, supplier);
    }
}
