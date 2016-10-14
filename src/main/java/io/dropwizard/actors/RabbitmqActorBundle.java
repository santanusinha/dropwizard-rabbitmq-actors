package io.dropwizard.actors;

import io.dropwizard.ConfiguredBundle;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.connectivity.config.RMQConfig;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.Getter;

/**
 * Created by santanu on 13/10/16.
 */
public abstract class RabbitmqActorBundle<T extends RabbitmqActorBundleConfiguration> implements ConfiguredBundle<T> {

    @Getter
    private RMQConnection connection;

    protected RabbitmqActorBundle() {
    }

    @Override
    public void run(T t, Environment environment) throws Exception {
        RMQConfig config = t.getRabbitmq();
        connection = new RMQConnection(config);
        environment.lifecycle().manage(connection);
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }
}
