package io.appform.dropwizard.actors.base.utils;

import io.appform.dropwizard.actors.utils.CommonUtils;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NamingUtils {
    public static final String NAMESPACE_ENV_NAME = "NAMESPACE_ENV_NAME";

    public String queueName(String prefix, String name) {
        final String nameWithPrefix = String.format("%s.%s", prefix, name);
        return prefixWithNamespace(nameWithPrefix);
    }

    public String sanitizeMetricName(String metric) {
        return metric == null ? null : metric.replaceAll("[^A-Za-z\\-0-9]", "").toLowerCase();
    }

    public String prefixWithNamespace(String name) {
        final String namespace = System.getenv(NAMESPACE_ENV_NAME);
        if (CommonUtils.isEmpty(namespace)) {
            return name;
        }
        return String.format("%s.%s", namespace, name);
    }

    public String getShardedQueueName(String queueName, int shardId) {
        return queueName + "_" + shardId;
    }

    public String getSideline(String queueName) {
        return String.format("%s_%s", queueName, "SIDELINE");
    }
}
