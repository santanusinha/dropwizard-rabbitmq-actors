package io.appform.dropwizard.actors.observers;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConsumeObserverContext {
    String queueName;
    boolean redelivered;
    MessageMetadata messageMetadata;
}
