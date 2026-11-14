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
import com.google.common.base.Preconditions;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.messaging.Messaging;
import io.appform.dropwizard.actors.messaging.config.MessagingConfig;
import io.appform.dropwizard.actors.metrics.RMQMetricObserver;
import io.appform.dropwizard.actors.observers.RMQObserver;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * A bundle to add RMQ actors
 */
@Slf4j
public abstract class RabbitmqActorBundle<T extends Configuration> implements ConfiguredBundle<T> {

    @Getter
    private ConnectionRegistry connectionRegistry;
    private Messaging messaging;
    private final List<RMQObserver> observers = new ArrayList<>();
    private RMQConfig rmqConfig;

    protected RabbitmqActorBundle() {

    }

    @Override
    public void run(T t, Environment environment) {
        this.rmqConfig = getConfig(t);
        val executorServiceProvider = getExecutorServiceProvider(t);
        val ttlConfig = ttlConfig();
        Preconditions.checkNotNull(executorServiceProvider, "Null executor service provider provided");
        val rootObserver = setupObservers(environment.metrics());
        Preconditions.checkNotNull(rootObserver, "Null root observer provided");
        this.connectionRegistry = new ConnectionRegistry(environment, executorServiceProvider, rmqConfig,
                ttlConfig == null ? TtlConfig.builder().build(): ttlConfig, rootObserver);
        environment.lifecycle().manage(connectionRegistry);
        val messagingConfig = getMessagingConfig(t);
        if (null != messagingConfig) {
            this.messaging = new Messaging(messagingConfig, rmqConfig, connectionRegistry,
                    environment.getObjectMapper());
            manageMessaging(environment.lifecycle(), messaging);
        }
    }

    /**
     * Ties messaging to the server lifecycle:
     * <ul>
     *   <li>managed right after the connection registry: topology and publishers start before any application managed
     *       object, so the application can publish while it starts, and stop after them, before connections close;</li>
     *   <li>consumers start when the server has started, i.e. after every managed object, so handlers never receive
     *       messages before the resources they use are ready. A failure fails server startup;</li>
     *   <li>consumers stop when the server begins stopping, before any managed object stops, so handlers never run
     *       against resources that are already shut down.</li>
     * </ul>
     */
    static void manageMessaging(final LifecycleEnvironment lifecycle, final Messaging messaging) {
        lifecycle.manage(messaging);
        lifecycle.addServerLifecycleListener(server -> {
            try {
                messaging.startConsumers();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to start messaging consumers", e);
            }
        });
        lifecycle.addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStopping(final LifeCycle event) {
                try {
                    messaging.stopConsumers();
                } catch (RuntimeException e) {
                    // Never block shutdown; Messaging.stop() stops whatever is left.
                    log.error("Error stopping messaging consumers", e);
                }
            }
        });
    }

    /**
     * Override to enable exchange-centric messaging ({@link Messaging}). Returns null (disabled) by default.
     */
    protected MessagingConfig getMessagingConfig(T t) {
        return null;
    }

    protected abstract TtlConfig ttlConfig();

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }

    /**
     * Exchange-centric messaging.
     *
     * <p>Register publishers and handlers in {@code Application.run}: registering from a managed object's
     * {@code start()} is too late, because messaging is managed by this bundle and starts first.
     *
     * @throws IllegalStateException when messaging is not configured
     */
    public Messaging getMessaging() {
        if (null == messaging) {
            throw new IllegalStateException("Messaging is not configured; override getMessagingConfig");
        }
        return messaging;
    }

    public RMQConnection getDefaultProducerConnection() {
        return connectionRegistry.createOrGet(Constants.DEFAULT_PRODUCER_CONNECTION_NAME);
    }

    public RMQConnection getDefaultConsumerConnection() {
        return connectionRegistry.createOrGet(Constants.DEFAULT_CONSUMER_CONNECTION_NAME);
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

    public void registerObserver(final RMQObserver observer) {
        if (null == observer) {
            return;
        }
        this.observers.add(observer);
        log.info("Registered observer: " + observer.getClass().getSimpleName());
    }

    private RMQObserver setupObservers(final MetricRegistry metricRegistry) {
        //Terminal observer calls the actual method
        RMQObserver rootObserver = new TerminalRMQObserver();
        for (var observer : observers) {
            if (null != observer) {
                rootObserver = observer.setNext(rootObserver);
            }
        }
        rootObserver = new RMQMetricObserver(this.rmqConfig, metricRegistry).setNext(rootObserver);
        log.info("Root observer is {}", rootObserver.getClass().getSimpleName());
        return rootObserver;
    }
}
