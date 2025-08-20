package io.appform.dropwizard.actors.observers;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublishObserverContext {
    String queueName;
    Map<String, Object> headers;
}
