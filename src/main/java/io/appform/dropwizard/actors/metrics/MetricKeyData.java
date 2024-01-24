package io.appform.dropwizard.actors.metrics;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class MetricKeyData {
    String queueName;
    String operation;
    boolean redelivered;
}
