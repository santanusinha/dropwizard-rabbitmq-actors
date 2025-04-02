package io.appform.dropwizard.actors.observers;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class ConsumeObserverContext {
    String queueName;
    boolean redelivered;
    Map<String, Object> headers;
}
