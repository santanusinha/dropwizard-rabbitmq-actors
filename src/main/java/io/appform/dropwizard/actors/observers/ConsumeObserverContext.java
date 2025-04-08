package io.appform.dropwizard.actors.observers;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConsumeObserverContext {
    String queueName;
    boolean redelivered;
    Map<String, Object> headers;
}
