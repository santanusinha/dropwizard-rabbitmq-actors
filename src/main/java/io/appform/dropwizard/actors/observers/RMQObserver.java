package io.appform.dropwizard.actors.observers;

import java.util.function.Function;
import lombok.Getter;

/**
 *
 */
public abstract class RMQObserver {

    @Getter
    private RMQObserver next;

    protected RMQObserver(RMQObserver next) {
        this.next = next;
    }

    public abstract <T> T executePublish(final PublishObserverContext context, final Function<PublishObserverContext, T> function);

    public abstract <T> T executeConsume(final ConsumeObserverContext context, final Function<ConsumeObserverContext, T> function);

    public final RMQObserver setNext(final RMQObserver next) {
        this.next = next;
        return this;
    }

    protected final <T> T proceedPublish(final PublishObserverContext context, final Function<PublishObserverContext, T> function) {
        if (null == next) {
            return function.apply(context);
        }
        return next.executePublish(context, function);
    }

    protected final <T> T proceedConsume(final ConsumeObserverContext context, final Function<ConsumeObserverContext, T> function) {
        if (null == next) {
            return function.apply(context);
        }
        return next.executeConsume(context, function);
    }
}