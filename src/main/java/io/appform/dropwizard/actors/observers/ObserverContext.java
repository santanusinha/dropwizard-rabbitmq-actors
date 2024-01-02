package io.appform.dropwizard.actors.observers;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;

import java.util.Map;

@Data
@Builder
@Setter
public class ObserverContext {
    String operation;
    String queueName;
    Map<String, Object> headers;
}
