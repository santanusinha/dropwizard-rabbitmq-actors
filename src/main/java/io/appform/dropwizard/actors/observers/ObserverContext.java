package io.appform.dropwizard.actors.observers;

import io.appform.dropwizard.actors.common.RMQOperation;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

import java.util.Map;

@Data
@Builder
@Setter
public class ObserverContext {
    RMQOperation operation;
    String queueName;
    Map<String, Object> headers;
}
