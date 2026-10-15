package io.appform.dropwizard.actors.connectivity.actor;

import io.appform.dropwizard.actors.observers.ConsumeMessageDetails;
import io.appform.dropwizard.actors.observers.ConsumeObserverContext;
import io.appform.dropwizard.actors.observers.PublishMessageDetails;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Recording observer that captures publish/consume observer contexts as they flow through
 * the observer chain during real publish/consume.
 */
public class RecordingObserver extends RMQObserver {

    private final List<String> publishQueueNames = new CopyOnWriteArrayList<>();
    private final List<String> consumeQueueNames = new CopyOnWriteArrayList<>();

    public RecordingObserver(final RMQObserver next) {
        super(next);
    }

    @Override
    public <T> T executePublish(final PublishObserverContext context,
                                final Function<PublishMessageDetails, T> publishFunction) {
        publishQueueNames.add(context.getQueueName());
        return proceedPublish(context, publishFunction);
    }

    @Override
    public <T> T executeConsume(final ConsumeObserverContext context,
                                final Function<ConsumeMessageDetails, T> consumeFunction) {
        consumeQueueNames.add(context.getQueueName());
        return proceedConsume(context, consumeFunction);
    }

    public List<String> getPublishQueueNames() {
        return publishQueueNames;
    }

    public List<String> getConsumeQueueNames() {
        return consumeQueueNames;
    }
}
