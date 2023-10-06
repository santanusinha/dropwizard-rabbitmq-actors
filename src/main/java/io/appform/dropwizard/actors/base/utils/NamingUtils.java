package io.appform.dropwizard.actors.base.utils;

import io.appform.dropwizard.actors.utils.CommonUtils;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class NamingUtils {
    public static final String NAMESPACE_ENV_NAME = "NAMESPACE_ENV_NAME";
    public static final String NAMESPACE_PROPERTY_NAME = "rmq.actors.namespace";

    public String queueName(String prefix, String name) {
        final String nameWithPrefix = String.format("%s.%s", prefix, name);
        return prefixWithNamespace(nameWithPrefix);
    }

    public String sanitizeMetricName(String metric) {
        return metric == null ? null : metric.replaceAll("[^A-Za-z\\-0-9]", "").toLowerCase();
    }

    public String prefixWithNamespace(String name) {
        String namespace = System.getenv(NAMESPACE_ENV_NAME);
        namespace = CommonUtils.isEmpty(namespace)
                    ? System.getProperty(NAMESPACE_PROPERTY_NAME, "")
                    : namespace;
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

    public String generateConsumerTag(String inputConsumerTag, int consumerNumber) {
        if (StringUtils.isBlank(inputConsumerTag)) {
            return StringUtils.EMPTY;
        }

        // There is limitation of 255 chars for consumer tags. So, quietly picking first 250 chars from the input.
        String tagPrefix = StringUtils.substring(inputConsumerTag, 0, 250);
        return tagPrefix + "_" + consumerNumber;
    }

    /**
     * Generates consumer tag with drove app and instance IDs.
     * e.g. my-service-1_0_215_AI-65e301a5-0833-410a-aa18-22f811b71eff
     * @return
     */
    public String populateDescriptiveConsumerTag() {
        String droveAppId = System.getenv("DROVE_APP_ID");
        String droveInstanceId = System.getenv("DROVE_INSTANCE_ID");
        return Stream.of(droveAppId, droveInstanceId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("_"));
    }
}
