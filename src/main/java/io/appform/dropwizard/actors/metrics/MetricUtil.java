package io.appform.dropwizard.actors.metrics;

import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class MetricUtil {

    private static final String RMQ_PREFIX = "rmqconnection";
    private static final String DELIMITER = ".";
    private static final String DELIMITER_REPLACEMENT = "_";

    public String getMetricPrefix(final MetricKeyData metricKeyData) {
        return getMetricPrefix(metricKeyData.getQueueName(), metricKeyData.getOperation());
    }

    private String getMetricPrefix(String... metricNames) {
        val metricPrefix = new StringBuilder(RMQ_PREFIX);
        for (val metricName : metricNames) {
            metricPrefix.append(DELIMITER).append(normalizeString(metricName));
        }
        return metricPrefix.toString();
    }

    private static String normalizeString(final String name) {
        return name.replace(DELIMITER, DELIMITER_REPLACEMENT);
    }
}
