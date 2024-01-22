package io.appform.dropwizard.actors.metrics;

import io.appform.dropwizard.actors.config.MetricConfig;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class MetricUtil {

    private static final String RMQ_PREFIX = "rmq.actor";
    private static final String REDELIVERED = "redelivered";
    private static final String DELIMITER = ".";
    private static final String DELIMITER_REPLACEMENT = "_";

    public boolean isMetricApplicable(final MetricConfig metricConfig, final String queueName) {
        if (metricConfig == null) {
            return false;
        }
        if (metricConfig.isEnabledForAll()) {
            return true;
        }
        return metricConfig.getEnabledForQueues() != null
                && metricConfig.getEnabledForQueues().contains(queueName);
    }

    public String getMetricPrefix(final MetricKeyData metricKeyData) {
        return getMetricPrefix(metricKeyData.getQueueName(), metricKeyData.getOperation());
    }

    public String getMetricPrefixForRedelivery(final MetricKeyData metricKeyData) {
        return getMetricPrefix(metricKeyData.getQueueName(), metricKeyData.getOperation(), REDELIVERED);
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
