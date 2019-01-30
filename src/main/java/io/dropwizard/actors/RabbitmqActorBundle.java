package io.dropwizard.actors;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.actors.config.RMQConfig;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A bundle to add RMQ actors
 */
@Slf4j
public abstract class RabbitmqActorBundle<T extends Configuration> implements ConfiguredBundle<T> {

    @Getter
    private RMQConnection connection;

    protected RabbitmqActorBundle() {
    }

    @Override
    public void run(T t, Environment environment) throws Exception {
        val config = getConfig(t);
        val metrics = getMetricRegistry(t);
        val executorService = getExecutorServiceProvider(t).newFixedThreadPool("rabbitmq-actors", config.getThreadPoolSize());
        connection = new RMQConnection(config, metrics, executorService);
        environment.lifecycle().manage(connection);
        environment.healthChecks().register("rabbitmq-actors", connection.healthcheck());
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }

    protected abstract RMQConfig getConfig(T t);

    /**
     * Provides metric registry for instrumenting RMQConnection. If method returns null, metrics collector for
     * RabbitMQ connection is not initialized.
     */

    protected MetricRegistry getMetricRegistry(T t) {
        return null;
    }

    /**
     * Provides implementation for {@link ExecutorServiceProvider}. Should be overridden if custom executor service
     * implementations needs to be used. For e.g. {@link com.codahale.metrics.InstrumentedExecutorService}.
     */
    protected ExecutorServiceProvider getExecutorServiceProvider(T t) {
        return (name, coreSize) -> Executors.newFixedThreadPool(coreSize);
    }
}
