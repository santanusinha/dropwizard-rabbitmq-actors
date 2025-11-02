package io.appform.dropwizard.actors.base.utils;

import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.ProducerConfig;
import io.appform.dropwizard.actors.actor.SidelineProcessorConfig;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.connectivity.strategy.ConnectionIsolationStrategy;
import io.appform.dropwizard.actors.connectivity.strategy.ConnectionIsolationStrategyVisitor;
import io.appform.dropwizard.actors.connectivity.strategy.DefaultConnectionStrategy;
import io.appform.dropwizard.actors.connectivity.strategy.SharedConnectionStrategy;
import io.appform.dropwizard.actors.utils.CommonUtils;
import javax.validation.Valid;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NamingUtils {

    public static final String NAMESPACE_ENV_NAME = "NAMESPACE_ENV_NAME";
    public static final String NAMESPACE_PROPERTY_NAME = "rmq.actors.namespace";

    public String queueName(String prefix, String name) {
        final String nameWithPrefix = String.format("%s.%s", prefix, name);
        return prefixWithNamespace(nameWithPrefix);
    }

    public String sidelineProcessorQueueName(final String prefix,
                                             final String name) {
        return getSidelineProcessor(queueName(prefix, name));
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

    public String getSidelineProcessor(final String queueName) {
        return String.format("%s_%s", queueName, "SIDELINE_PROCESSOR");
    }

    public String producerConnectionName(ProducerConfig producerConfig) {
        if (producerConfig == null) {
            return Constants.DEFAULT_PRODUCER_CONNECTION_NAME;
        }
        return deriveConnectionName(producerConfig.getConnectionIsolationStrategy(),
                Constants.DEFAULT_PRODUCER_CONNECTION_NAME);
    }

    public String consumerConnectionName(ConsumerConfig consumerConfig) {
        if (consumerConfig == null) {
            return Constants.DEFAULT_CONSUMER_CONNECTION_NAME;
        }

        return deriveConnectionName(consumerConfig.getConnectionIsolationStrategy(),
                Constants.DEFAULT_CONSUMER_CONNECTION_NAME);
    }


    public String sidelineProcessorConnectionName(final SidelineProcessorConfig sidelineProcessorConfig) {
        if (sidelineProcessorConfig == null || sidelineProcessorConfig.getConsumerConfig() == null) {
            return Constants.DEFAULT_SIDELINE_PROCESSOR_CONNECTION_NAME;
        }

        return deriveConnectionName(sidelineProcessorConfig.getConsumerConfig().getConnectionIsolationStrategy(),
                Constants.DEFAULT_SIDELINE_PROCESSOR_CONNECTION_NAME);
    }

    public String deriveConnectionName(ConnectionIsolationStrategy isolationStrategy, String defaultConnectionName) {
        if (isolationStrategy == null) {
            return defaultConnectionName;
        }

        return isolationStrategy.accept(new ConnectionIsolationStrategyVisitor<>() {

            @Override
            public String visit(SharedConnectionStrategy strategy) {
                return strategy.getName();
            }

            @Override
            public String visit(DefaultConnectionStrategy strategy) {
                return defaultConnectionName;
            }

        });
    }
}
