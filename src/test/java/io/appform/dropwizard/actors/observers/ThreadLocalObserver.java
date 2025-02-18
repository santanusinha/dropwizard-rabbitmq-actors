package io.appform.dropwizard.actors.observers;

import io.appform.dropwizard.actors.actor.MessageConsumeFunction;
import io.appform.dropwizard.actors.actor.MessagePublishFunction;
import org.slf4j.MDC;

public class ThreadLocalObserver extends RMQObserver {

    public ThreadLocalObserver(RMQObserver next) {
        super(next);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, MessagePublishFunction<T> supplier) {
        MDC.put(ObserverTestUtil.PUBLISH_START, context.getQueueName());
        try {
            return proceedPublish(context, supplier);
        } finally {
            MDC.put(ObserverTestUtil.PUBLISH_END, context.getQueueName());
        }
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, MessageConsumeFunction<T> supplier) {
            return null;
    }
}
