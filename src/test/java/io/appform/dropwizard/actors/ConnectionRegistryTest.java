package io.appform.dropwizard.actors;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.Lists;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.common.ErrorCode;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.ConnectionConfig;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;

import javax.validation.Validation;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertTrue;

public class ConnectionRegistryTest {

    private ConnectionRegistry registry;

    private Environment environment;

    @Before
    public void setup() {

        environment = new Environment("testing",
                null,
                Validation.buildDefaultValidatorFactory(),
                new MetricRegistry(),
                Thread.currentThread().getContextClassLoader(),
                new HealthCheckRegistry(),
                new Configuration());
    }

    @Test
    public void testCustomConnectionCreationWithReservedNameFails() {
        RMQConfig config = new RMQConfig();
        config.setConnections(Lists.newArrayList(ConnectionConfig.builder().name(Constants.DEFAULT_CONSUMER_CONNECTION_NAME).build()));

        registry = new ConnectionRegistry(environment,
                (name, coreSize) -> Executors.newFixedThreadPool(1),
                config, TtlConfig.builder().build());

        Constants.DEFAULT_CONNECTIONS.forEach(defaultConnectionName -> {
            try {
                registry.createOrGet(defaultConnectionName);
            } catch (Exception ex) {
                assertTrue(ex instanceof RabbitmqActorException &&
                        ((RabbitmqActorException) ex).getErrorCode().equals(ErrorCode.CONNECTION_NAME_RESERVED_FOR_INTERNAL_USE));
                assertTrue(ex.getMessage().startsWith("These connection names are reserved for internal usage: ") &&
                        ex.getMessage().contains(defaultConnectionName));
            }
        });
    }


}
