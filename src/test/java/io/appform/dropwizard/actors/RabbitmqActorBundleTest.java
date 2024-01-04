package io.appform.dropwizard.actors;

import com.codahale.metrics.MetricRegistry;
import io.appform.dropwizard.actors.config.MetricConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.actor.RabbitMQBundleTestAppConfiguration;
import io.appform.dropwizard.actors.metrics.RMQMetricObserver;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;


class RabbitmqActorBundleTest {
    private RabbitmqActorBundle actorBundleImpl;
    private RMQConfig config;
    private final MetricRegistry metricRegistry = new MetricRegistry();

    @BeforeEach
    public void setup() {
        this.config = RMQConfig.builder()
                .brokers(new ArrayList<>())
                .userName("")
                .threadPoolSize(1)
                .password("")
                .secure(false)
                .startupGracePeriodSeconds(1)
                .metricConfig(MetricConfig.builder().enabledForAll(true).build())
                .build();
        actorBundleImpl = new RabbitmqActorBundle<RabbitMQBundleTestAppConfiguration>() {
            @Override
            protected TtlConfig ttlConfig() {
                return TtlConfig.builder()
                        .ttl(Duration.ofMinutes(30))
                        .ttlEnabled(true)
                        .build();
            }
            @Override
            protected RMQConfig getConfig(RabbitMQBundleTestAppConfiguration rabbitMQBundleTestAppConfiguration) {
                return config;
            }
        };
    }

    @Test
    void testObserverChain() {
        val publishMetricObserver = new RMQMetricObserver(config, metricRegistry);
        Environment environment = Mockito.mock(Environment.class);
        LifecycleEnvironment lifecycle = Mockito.mock(LifecycleEnvironment.class);
        Mockito.doReturn(metricRegistry).when(environment).metrics();
        Mockito.doReturn(lifecycle).when(environment).lifecycle();
        Mockito.doNothing().when(lifecycle).manage(ArgumentMatchers.any(ConnectionRegistry.class));
        actorBundleImpl.registerObserver(publishMetricObserver);
        actorBundleImpl.run(new RabbitMQBundleTestAppConfiguration(), environment);
        Assertions.assertEquals(actorBundleImpl.getRootObserver().getNext(), publishMetricObserver);
    }

}