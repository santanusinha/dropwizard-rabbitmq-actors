package io.appform.dropwizard.actors.tracing;

import io.appform.dropwizard.actors.observers.ConsumeObserverContext;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author ankush.nakaskar
 */
@Slf4j
public class MDCTracingRMQObserver extends RMQObserver {

    public MDCTracingRMQObserver(RMQObserver next) {
        super(next);
    }
    @Override
    public <T, R> R executePublish(PublishObserverContext context, Function<PublishObserverContext, R> function) {
        try {
            return proceedPublish(context, function);
        } catch (Throwable t) {
            throw t;
        }
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, Supplier<T> supplier) {
        try {
            return proceedConsume(context, supplier);
        } catch (Throwable t) {
            throw t;
        }
    }

}