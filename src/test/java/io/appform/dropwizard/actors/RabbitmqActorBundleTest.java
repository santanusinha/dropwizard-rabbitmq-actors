package io.appform.dropwizard.actors;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.config.MetricConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.connectivity.actor.RabbitMQBundleTestAppConfiguration;
import io.appform.dropwizard.actors.observers.ObserverTestUtil;
import io.appform.dropwizard.actors.observers.ThreadLocalObserver;
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
    private RMQConnection connection;
    private Channel publishChannel;

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
        this.connection = Mockito.mock(RMQConnection.class);
        this.publishChannel = Mockito.mock(Channel.class);
    }

    @Test
    void testObserverChain() throws Exception {
        val threadLocalObserver = new ThreadLocalObserver(null);
        Environment environment = Mockito.mock(Environment.class);
        LifecycleEnvironment lifecycle = Mockito.mock(LifecycleEnvironment.class);
        Mockito.doReturn(metricRegistry).when(environment).metrics();
        Mockito.doReturn(lifecycle).when(environment).lifecycle();
        Mockito.doNothing().when(lifecycle).manage(ArgumentMatchers.any(ConnectionRegistry.class));
        actorBundleImpl.registerObserver(threadLocalObserver);
        actorBundleImpl.run(new RabbitMQBundleTestAppConfiguration(), environment);
        Assertions.assertEquals(actorBundleImpl.getConnectionRegistry().getRootObserver().getNext(), threadLocalObserver);

        Mockito.doReturn(actorBundleImpl.getConnectionRegistry().getRootObserver()).when(connection).getRootObserver();
        Mockito.doReturn(publishChannel).when(connection).newChannel();
        Mockito.doNothing().when(publishChannel).basicPublish(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());

        val queueName = "queue-1";
        val objectMapper = new ObjectMapper();
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        val message = ImmutableMap.of("key", "value");
        val publisher = new UnmanagedPublisher<>(queueName, actorConfig, connection, objectMapper);
        val channel = Mockito.mock(Channel.class);
        Mockito.doReturn(channel).when(connection).channel();
        Mockito.doReturn(null).when(channel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());
        publisher.start();
        publisher.publish(message);

        ObserverTestUtil.validateThreadLocal(NamingUtils.queueName(actorConfig.getPrefix(), queueName));
    }

}