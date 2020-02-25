/*
 * Copyright (c) 2019 Santanu Sinha <santanu.sinha@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.appform.dropwizard.actors;

import com.codahale.metrics.MetricRegistry;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.Executors;

/**
 * A bundle to add RMQ actors
 */
@Slf4j
public abstract class RabbitmqActorBundle<T extends Configuration> implements ConfiguredBundle<T> {

    @Getter
    private RMQConnection connection;

    @Getter
    private ConnectionRegistry connectionRegistry;

    private ExecutorServiceProvider executorServiceProvider;

    private MetricRegistry metricRegistry;

    protected RabbitmqActorBundle(final MetricRegistry metricRegistry,
                                  final ExecutorServiceProvider executorServiceProvider) {
        this.metricRegistry = metricRegistry;
        this.executorServiceProvider = executorServiceProvider;
    }

    @Override
    public void run(T t, Environment environment) throws Exception {
        val config = getConfig(t);
        this.connectionRegistry = new ConnectionRegistry(environment, executorServiceProvider, metricRegistry,
                config);
        this.connection = connectionRegistry.createOrGet(Constants.DEFAULT_CONNECTION_NAME);
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }

    protected abstract RMQConfig getConfig(T t);

    /**
     * Provides metric registry for instrumenting RMQConnection. If method returns null, default metric registry from
     * dropwizard environment is picked
     */

    /**
     * Provides implementation for {@link ExecutorServiceProvider}. Should be overridden if custom executor service
     * implementations needs to be used. For e.g. {@link com.codahale.metrics.InstrumentedExecutorService}.
     */
    protected ExecutorServiceProvider getExecutorServiceProvider(T t) {
        return (name, coreSize) -> Executors.newFixedThreadPool(coreSize);
    }

    private MetricRegistry metrics(Environment environment) {
        if (this.metricRegistry != null) {
            return this.metricRegistry;
        }
        return environment.metrics();
    }

    private ExecutorServiceProvider executorServiceProvider() {
        if (this.executorServiceProvider != null) {
            return executorServiceProvider;
        }
        return (name, coreSize) -> Executors.newFixedThreadPool(coreSize);
    }

}
