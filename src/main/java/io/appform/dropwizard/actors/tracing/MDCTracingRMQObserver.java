package io.appform.dropwizard.actors.tracing;

import io.appform.dropwizard.actors.observers.ConsumeObserverContext;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import io.appform.dropwizard.actors.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.function.Supplier;

/**
 * @author ankush.nakaskar
 */
@Slf4j
public class MDCTracingRMQObserver extends RMQObserver {

    public MDCTracingRMQObserver() {
        super(null);
    }

    @Override
    public <T> T executePublish(PublishObserverContext context, Supplier<T> supplier) {
        try {
            val response = proceedPublish(context, supplier);
            return response;
        } catch (Throwable t) {
            throw t;
        }
    }

    @Override
    public <T> T executeConsume(ConsumeObserverContext context, Supplier<T> supplier) {
        try {
            CommonUtils.populateTraceId(context);
            val response = proceedConsume(context, supplier);
            return response;
        } catch (Throwable t) {
            throw t;
        }
    }

}
