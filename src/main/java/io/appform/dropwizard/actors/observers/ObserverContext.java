package io.appform.dropwizard.actors.observers;

import io.appform.dropwizard.actors.common.RMQOperation;
import lombok.Builder;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;

@Value
@Builder
public class ObserverContext {
    RMQOperation operation;
    String queueName;
    Map<String, Object> headers = new HashMap<>();
}
