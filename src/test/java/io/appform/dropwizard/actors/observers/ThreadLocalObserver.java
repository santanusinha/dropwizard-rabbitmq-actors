package io.appform.dropwizard.actors.observers;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import org.slf4j.MDC;

import java.util.function.Function;

public class ThreadLocalObserver extends RMQObserver {

    public ThreadLocalObserver(RMQObserver next) {
        super(next);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, Function<PublishMessageDetails, T> publishFunction) {
        MDC.put(ObserverTestUtil.PUBLISH_START, context.getQueueName());
        try {
            return proceedPublish(context, publishFunction);
        } finally {
            MDC.put(ObserverTestUtil.PUBLISH_END, context.getQueueName());
        }
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, Function<ConsumeMessageDetails, T> consumeFunction) {
        return null;
    }
}
