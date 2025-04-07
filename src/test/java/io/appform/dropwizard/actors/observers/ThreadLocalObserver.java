package io.appform.dropwizard.actors.observers;

import org.slf4j.MDC;

import java.util.function.Function;

public class ThreadLocalObserver extends RMQObserver {

    public ThreadLocalObserver(RMQObserver next) {
        super(next);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, Function<PublishObserverContext, T> function) {
        MDC.put(ObserverTestUtil.PUBLISH_START, context.getQueueName());
        try {
            return proceedPublish(context, function);
        } finally {
            MDC.put(ObserverTestUtil.PUBLISH_END, context.getQueueName());
        }
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, Function<ConsumeObserverContext, T> function) {
            return null;
    }
}
