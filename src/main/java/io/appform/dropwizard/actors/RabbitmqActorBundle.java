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

import com.google.common.base.Preconditions;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.common.Constants;
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
    private ConnectionRegistry connectionRegistry;

    private RMQConfig rmqConfig;

    protected RabbitmqActorBundle() {

    }

    @Override
    public void run(T t, Environment environment) {
        this.rmqConfig = getConfig(t);
        val executorServiceProvider = getExecutorServiceProvider(t);
        val ttlConfig = ttlConfig();
        Preconditions.checkNotNull(executorServiceProvider, "Null executor service provider provided");
        this.connectionRegistry = new ConnectionRegistry(environment, executorServiceProvider, rmqConfig,
                ttlConfig == null ? TtlConfig.builder().build(): ttlConfig);
        environment.lifecycle().manage(connectionRegistry);
    }

    protected abstract TtlConfig ttlConfig();

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }

    public RMQConnection getConnection() {
        return connectionRegistry.createOrGet(Constants.DEFAULT_CONNECTION_NAME);
    }

    protected abstract RMQConfig getConfig(T t);

    /**
     * Provides implementation for {@link ExecutorServiceProvider}. Should be overridden if custom executor service
     * implementations needs to be used. For e.g. {@link com.codahale.metrics.InstrumentedExecutorService}.
     * @param t param to accept custom config
     * @return io.appform.dropwizard.actors.ExecutorServiceProvider
     */
    protected ExecutorServiceProvider getExecutorServiceProvider(T t) {
        return (name, coreSize) -> Executors.newFixedThreadPool(coreSize);
    }
}
