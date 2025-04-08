package io.appform.dropwizard.actors.observers;

import java.util.function.Function;
import org.slf4j.MDC;

public class ThreadLocalObserver extends RMQObserver {

    public ThreadLocalObserver(RMQObserver next) {
        super(next);
    }

    @Override
    public <T> T executePublish(final PublishObserverContext context, final Function<PublishObserverContext, T> function) {
        MDC.put(ObserverTestUtil.PUBLISH_START, context.getQueueName());
        try {
            return proceedPublish(context, function);
        } finally {
            MDC.put(ObserverTestUtil.PUBLISH_END, context.getQueueName());
        }
    }

    @Override
    public <T> T executeConsume(final ConsumeObserverContext context, final Function<ConsumeObserverContext, T> function) {
        return null;
    }
}
