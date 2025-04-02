package io.appform.dropwizard.actors.observers;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class PublishObserverContext {
    String queueName;
    Map<String, Object> headers;

}
