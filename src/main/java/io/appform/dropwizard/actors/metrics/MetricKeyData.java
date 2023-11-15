package io.appform.dropwizard.actors.metrics;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Builder
@Data
@Value
public class MetricKeyData {
    String queueName;
    String operation;
}
