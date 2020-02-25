package io.appform.dropwizard.actors;

import com.codahale.metrics.MetricRegistry;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.setup.Environment;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class ConnectionRegistry {

    private ConcurrentHashMap<String, RMQConnection> connections;
    private final Environment environment;
    private final ExecutorServiceProvider executorServiceProvider;
    private final MetricRegistry metricRegistry;
    private final RMQConfig rmqConfig;

    public ConnectionRegistry(final Environment environment,
                              final ExecutorServiceProvider executorServiceProvider,
                              final MetricRegistry metricRegistry,
                              final RMQConfig rmqConfig) {
        this.environment = environment;
        this.executorServiceProvider = executorServiceProvider;
        this.metricRegistry = metricRegistry;
        this.rmqConfig = rmqConfig;
    }

    public RMQConnection createOrGet(String connectionName) {
        return connections.computeIfAbsent(connectionName, connection -> {
            log.info(String.format("Creating new RMQ connection with name [%s]", connectionName));
            RMQConnection rmqConnection = new RMQConnection(connectionName, rmqConfig, metricRegistry,
                    executorServiceProvider.newFixedThreadPool(String.format("rabbitmq-actors.%s", connection), rmqConfig.getThreadPoolSize()));
            environment.lifecycle().manage(rmqConnection);
            environment.healthChecks().register(String.format("rabbitmq-actors.%s", connection), rmqConnection.healthcheck());
            log.info(String.format("Created new RMQ connection with name [%s]", connectionName));
            return null;
        });
    }


}
