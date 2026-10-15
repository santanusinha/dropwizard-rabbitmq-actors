package io.appform.dropwizard.actors.connectivity.actor;

import io.appform.dropwizard.actors.RabbitmqActorBundle;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Environment;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Test application registering {@link RabbitmqActorBundle} with a registered observer,
 * so that observer chaining through the bundle is exercised in a real app.
 */
public class RabbitMQBundleObserverTestApp extends Application<RabbitMQBundleTestAppConfiguration> {

    private static final AtomicReference<RMQConfig> RMQ_CONFIG_HOLDER =
            new AtomicReference<>();

    @Getter
    private RabbitmqActorBundle<RabbitMQBundleTestAppConfiguration> actorBundle;

    @Getter
    private final RecordingObserver recordingObserver = new RecordingObserver(null);

    public static void setRmqConfig(final RMQConfig rmqConfig) {
        RMQ_CONFIG_HOLDER.set(rmqConfig);
    }

    @Override
    public void run(final RabbitMQBundleTestAppConfiguration configuration,
                    final Environment environment) {
        this.actorBundle = new RabbitmqActorBundle<>() {
            @Override
            protected TtlConfig ttlConfig() {
                return TtlConfig.builder().build();
            }

            @Override
            protected RMQConfig getConfig(final RabbitMQBundleTestAppConfiguration appConfiguration) {
                return RMQ_CONFIG_HOLDER.get();
            }
        };
        actorBundle.registerObserver(recordingObserver);
        actorBundle.run(configuration, environment);
    }
}
