package io.appform.dropwizard.actors.retry.config;

import io.appform.dropwizard.actors.config.TracingConfiguration;

import java.util.concurrent.Callable;

public class ConfigProvider {
    private final Callable<TracingConfiguration> dynamicConfig;
    private final TracingConfiguration defaultConfig;

    public ConfigProvider(final Callable<TracingConfiguration> dynamicConfig, final TracingConfiguration defaultConfig) {
        this.dynamicConfig = dynamicConfig;
        this.defaultConfig = defaultConfig;
    }

    public TracingConfiguration getTracingConfiguration() throws Exception {
        if (dynamicConfig == null || dynamicConfig.call() == null) {
            return defaultConfig;
        }
        return dynamicConfig.call();
    }
}
