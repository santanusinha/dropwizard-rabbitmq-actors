package io.appform.dropwizard.actors.observers;

import io.appform.dropwizard.actors.actor.MessageConsumeFunction;
import io.appform.dropwizard.actors.actor.MessagePublishFunction;
import lombok.Getter;

public abstract class RMQObserver {

    @Getter
    private RMQObserver next;

    protected RMQObserver(RMQObserver next) {
        this.next = next;
    }

    public abstract <T> T executePublish(final PublishObserverContext context, final MessagePublishFunction<T> publishFunction);

    public abstract <T> T executeConsume(final ConsumeObserverContext context, final MessageConsumeFunction<T> consumeFunction);

    public final RMQObserver setNext(final RMQObserver next) {
        this.next = next;
        return this;
    }

    protected final <T> T proceedPublish(final PublishObserverContext context,
                                         final MessagePublishFunction<T> publishFunction) {
        if (null == next) {
            return publishFunction.apply(context.getMessageProperties());
        }
        return next.executePublish(context, publishFunction);
    }

    protected final <T> T proceedConsume(final ConsumeObserverContext context, final MessageConsumeFunction<T> consumeFunction) {
        if (null == next) {
            return consumeFunction.apply(context.getMessageMetadata());
        }
        return next.executeConsume(context, consumeFunction);
    }
}
