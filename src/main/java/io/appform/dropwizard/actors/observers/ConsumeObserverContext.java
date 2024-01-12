package io.appform.dropwizard.actors.observers;

import io.appform.dropwizard.actors.common.RMQOperation;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConsumeObserverContext {
    RMQOperation operation;
    String queueName;
    boolean redelivered;
}
