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
        connection = new RMQConnection(getConfig(t), getMetricRegistry(t), getExecutorServiceProvider(t));
        environment.lifecycle().manage(connection);
        environment.healthChecks().register("rabbitmq-actors", connection.healthcheck());
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }

    protected abstract RMQConfig getConfig(T t);

    protected abstract MetricRegistry getMetricRegistry(T t);

    protected abstract ExecutorServiceProvider getExecutorServiceProvider(T t);
}
