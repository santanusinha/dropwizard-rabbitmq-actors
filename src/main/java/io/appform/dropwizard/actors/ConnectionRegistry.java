package io.appform.dropwizard.actors;

import com.codahale.metrics.MetricRegistry;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.ConnectionConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
@Data
public class ConnectionRegistry implements Managed {

    private final ConcurrentHashMap<String, RMQConnection> connections;
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
        this.connections = new ConcurrentHashMap<>();
    }

    public RMQConnection createOrGet(ConnectionConfig config) {
        return connections.computeIfAbsent(config.getName(), connection -> {
            log.info(String.format("Creating new RMQ connection with name [%s]", connection));
            RMQConnection rmqConnection = new RMQConnection(connection, rmqConfig, metricRegistry,
                    executorServiceProvider.newFixedThreadPool(String.format("rabbitmq-actors.%s", connection),
                            config.getThreadPoolSize()));
            try {
                rmqConnection.start();
            } catch (Exception e) {
                throw RabbitmqActorException.propagate(e);
            }
            environment.healthChecks().register(String.format("rabbitmq-actors.%s", connection), rmqConnection.healthcheck());
            log.info(String.format("Created new RMQ connection with name [%s]", connection));
            return rmqConnection;
        });
    }


    @Override
    public void start() throws Exception {

    }

    @Override
    public void stop() throws Exception {
        connections.forEach(new BiConsumer<String, RMQConnection>() {
            @SneakyThrows
            @Override
            public void accept(String name, RMQConnection rmqConnection) {
                rmqConnection.stop();
            }
        });
    }
}
