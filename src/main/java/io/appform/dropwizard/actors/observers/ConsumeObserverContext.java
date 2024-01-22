package io.appform.dropwizard.actors.observers;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConsumeObserverContext {
    String queueName;
    boolean redelivered;
}
