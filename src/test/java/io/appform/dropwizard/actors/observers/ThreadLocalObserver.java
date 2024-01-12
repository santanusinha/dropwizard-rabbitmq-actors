package io.appform.dropwizard.actors.observers;

import org.slf4j.MDC;

import java.util.function.Supplier;

public class ThreadLocalObserver extends RMQObserver {

    public ThreadLocalObserver(RMQObserver next) {
        super(next);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, Supplier<T> supplier) {
        MDC.put(ObserverTestUtil.PUBLISH_START, context.getQueueName());
        try {
            return proceedPublish(context, supplier);
        } finally {
            MDC.put(ObserverTestUtil.PUBLISH_END, context.getQueueName());
        }
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, Supplier<T> supplier) {
            return null;
    }
}
