package io.appform.dropwizard.actors.metrics;

import io.appform.dropwizard.actors.common.RMQOperation;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Builder
@Value
public class MetricKeyData {
    String queueName;
    RMQOperation operation;
}
