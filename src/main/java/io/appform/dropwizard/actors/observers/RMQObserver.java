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

    public abstract <T> T executePublish(final PublishObserverContext context, final Function<PublishMessageDetails, T> publishFunction);

    public abstract <T> T executeConsume(final ConsumeObserverContext context, final Function<ConsumeMessageDetails, T> consumeFunction);

    public final RMQObserver setNext(final RMQObserver next) {
        this.next = next;
        return this;
    }

    protected final <T> T proceedPublish(final PublishObserverContext context,
                                         final Function<PublishMessageDetails, T> publishFunction) {
        if (null == next) {
            return publishFunction.apply(PublishMessageDetails.builder().messageProperties(context.getMessageProperties()).build());
        }
        return next.executePublish(context, publishFunction);
    }

    protected final <T> T proceedConsume(final ConsumeObserverContext context, final Function<ConsumeMessageDetails, T> consumeFunction) {
        if (null == next) {
            return consumeFunction.apply(ConsumeMessageDetails.builder().messageMetadata(context.getMessageMetadata()).build());
        }
        return next.executeConsume(context, consumeFunction);
    }
}
