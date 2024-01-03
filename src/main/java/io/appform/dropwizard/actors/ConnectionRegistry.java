package io.appform.dropwizard.actors;

import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.common.ErrorCode;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.ConnectionConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
@Data
public class ConnectionRegistry implements Managed {

    private final ConcurrentHashMap<String, RMQConnection> connections;
    private final Environment environment;
    private final ExecutorServiceProvider executorServiceProvider;
    private final RMQConfig rmqConfig;
    private TtlConfig ttlConfig;

    public ConnectionRegistry(final Environment environment,
                              final ExecutorServiceProvider executorServiceProvider,
                              final RMQConfig rmqConfig,
                              final TtlConfig ttlConfig) {
        this.environment = environment;
        this.executorServiceProvider = executorServiceProvider;
        this.rmqConfig = rmqConfig;
        this.ttlConfig = ttlConfig;
        this.connections = new ConcurrentHashMap<>();
    }

    public RMQConnection createOrGet(String connectionName) {
        val threadPoolSize = determineThreadPoolSize(connectionName);
        return createOrGet(connectionName, threadPoolSize);
    }

    public RMQConnection createOrGet(String connectionName, int threadPoolSize) {

        if (Constants.DEFAULT_CONNECTIONS.contains(connectionName)) {
            throw new RabbitmqActorException(ErrorCode.CONNECTION_NAME_RESERVED_FOR_INTERNAL_USE,
                    "Please don't use default connection names", null);
        }

        return connections.computeIfAbsent(connectionName, connection -> {
            log.info(String.format("Creating new RMQ connection with name [%s] having [%d] threads", connection,
                    threadPoolSize));
            val rmqConnection = new RMQConnection(
                    connection,
                    rmqConfig,
                    executorServiceProvider.newFixedThreadPool(String.format("rmqconnection-%s", connection),
                            threadPoolSize),
                    environment, ttlConfig);
            try {
                rmqConnection.start();
            } catch (Exception e) {
                throw RabbitmqActorException.propagate(e);
            }
            log.info(String.format("Created new RMQ connection with name [%s]", connection));
            return rmqConnection;
        });
    }

    private int determineThreadPoolSize(String connectionName) {
        if (Constants.DEFAULT_CONNECTIONS.contains(connectionName)) {
            return rmqConfig.getThreadPoolSize();
        }

        if (rmqConfig.getConnections() == null) {
            return Constants.DEFAULT_THREADS_PER_CONNECTION;
        }

        return rmqConfig.getConnections().stream()
                .filter(x -> Objects.equals(x.getName(), connectionName))
                .findAny()
                .map(ConnectionConfig::getThreadPoolSize)
                .orElse(Constants.DEFAULT_THREADS_PER_CONNECTION);
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
        connections.forEach(new BiConsumer<String, RMQConnection>() {
            @SneakyThrows
            @Override
            public void accept(String name, RMQConnection rmqConnection) {
                rmqConnection.stop();
            }
        });
    }
}
