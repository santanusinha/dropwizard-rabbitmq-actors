package io.appform.dropwizard.actors.observers;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import lombok.Getter;

import java.util.function.Function;

public abstract class RMQObserver {

    @Getter
    private RMQObserver next;

    protected RMQObserver(RMQObserver next) {
        this.next = next;
    }

    public abstract <T> T executePublish(final PublishObserverContext context, final Function<AMQP.BasicProperties, T> publishFunction);

    public abstract <T> T executeConsume(final ConsumeObserverContext context, final Function<MessageMetadata, T> consumeFunction);

    public final RMQObserver setNext(final RMQObserver next) {
        this.next = next;
        return this;
    }

    protected final <T> T proceedPublish(final PublishObserverContext context,
                                         final Function<AMQP.BasicProperties, T> publishFunction) {
        if (null == next) {
            return publishFunction.apply(context.getMessageProperties());
        }
        return next.executePublish(context, publishFunction);
    }

    protected final <T> T proceedConsume(final ConsumeObserverContext context, final Function<MessageMetadata, T> consumeFunction) {
        if (null == next) {
            return consumeFunction.apply(context.getMessageMetadata());
        }
        return next.executeConsume(context, consumeFunction);
    }
}
