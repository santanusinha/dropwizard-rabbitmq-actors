package io.appform.dropwizard.actors.base.utils;

import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.utils.CommonUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NamingUtils {

    public static final String NAMESPACE_ENV_NAME = "NAMESPACE_ENV_NAME";

    public static String queueName(String prefix, String name) {
        final String nameWithPrefix = String.format("%s.%s", prefix, name);
        return prefixWithNamespace(nameWithPrefix);
    }

    public static String sanitizeMetricName(String metric) {
        return metric == null ? null : metric.replaceAll("[^A-Za-z\\-0-9]", "").toLowerCase();
    }

    public static String prefixWithNamespace(String name) {
        final String namespace = System.getenv(NAMESPACE_ENV_NAME);
        if (CommonUtils.isEmpty(namespace)) {
            return name;
        }
        return String.format("%s.%s", namespace, name);
    }

    public static String getRoutingKey(String queueName, ActorConfig config) {
        String routingKey = queueName;
        if (config.isSharded()) {
            int shardId = getShardId(config);
            routingKey = queueName + "_" + shardId;
        }
        return routingKey;
    }

    public static String getShardedQueueName(String queueName, int shardId) {
        return queueName + "_" + shardId;
    }

    private static int getShardId(ActorConfig config) {
        return RandomUtils.nextInt(0, config.getShardCount());
    }

}
