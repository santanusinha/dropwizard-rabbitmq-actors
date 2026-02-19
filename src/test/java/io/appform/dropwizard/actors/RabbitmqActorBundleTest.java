package io.appform.dropwizard.actors;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


class RabbitmqActorBundleTest {
    private RabbitmqActorBundle actorBundleImpl;
    private RMQConfig config;
    private final MetricRegistry metricRegistry = new MetricRegistry();
    private RMQConnection connection;
    private Channel publishChannel;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() throws Exception {
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
        val threadLocalObserver = new ThreadLocalObserver(null);
        this.objectMapper = new ObjectMapper();
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
    }

    @Test
    void testObserverChain() throws Exception {
        Channel channel = Mockito.mock(Channel.class);
        val queueName = "queue-1";
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        val message = ImmutableMap.of("key", "value");
        val publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        Mockito.doReturn(channel).when(connection).channel();
        Mockito.doReturn(null).when(channel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());
        publisher.start();
        publisher.publish(message);
        ObserverTestUtil.validateThreadLocal(NamingUtils.queueName(actorConfig.getPrefix(), queueName));
    }

    @Test
    void testObserveChainForTtlQueue() throws Exception {
        Channel channel = Mockito.mock(Channel.class);
        val delayedQueueName = "queue-delayed-1";
        val delayedActorConfig = new ActorConfig();
        delayedActorConfig.setExchange("test-exchange-2");
        delayedActorConfig.setDelayed(true);
        delayedActorConfig.setDelayType(DelayType.TTL);
        val message = ImmutableMap.of("key", "value");
        Mockito.doReturn(channel).when(connection).channel();
        val delayedPublisher = new UnmanagedPublisher<>(NamingUtils.queueName(delayedActorConfig.getPrefix(), delayedQueueName), delayedActorConfig, connection, objectMapper);
        Mockito.doReturn(null).when(channel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());
        delayedPublisher.start();

        verify(channel, times(1)).exchangeDeclare(
                "test-exchange-2",
                "direct",
                true
        );
        verify(channel, times(1)).exchangeDeclare(
                "test-exchange-2_TTL",
                "direct",
                true
        );

        delayedPublisher.publish(message);

        ObserverTestUtil.validateThreadLocal(NamingUtils.queueName(delayedActorConfig.getPrefix(), delayedQueueName));
    }

    // ---- Publisher Concurrency: Channel Pool Creation ----

    /**
     * Verifies that setting publisherConcurrency=N creates exactly N publisher channels.
     * Each channel must be a distinct object, and messages published should be
     * distributed across the entire pool (random selection).
     */
    @Test
    void testPublisherConcurrency() throws Exception {
        Channel channel = Mockito.mock(Channel.class);
        val queueName = "queue-concurrent";
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-concurrent");
        actorConfig.setPublisherConcurrency(3);
        val message = ImmutableMap.of("key", "value");

        // Each newChannel() call returns a distinct mock so we can verify separate channels are created
        Channel channel1 = Mockito.mock(Channel.class);
        Channel channel2 = Mockito.mock(Channel.class);
        Channel channel3 = Mockito.mock(Channel.class);
        Mockito.doNothing().when(channel1).basicPublish(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doNothing().when(channel2).basicPublish(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doNothing().when(channel3).basicPublish(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.doReturn(channel1).doReturn(channel2).doReturn(channel3).when(connection).newChannel();

        Mockito.doReturn(channel).when(connection).channel();
        Mockito.doReturn(null).when(channel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());

        val publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        publisher.start();

        // Verify exactly 3 channels were created (one per publisherConcurrency)
        verify(connection, times(3)).newChannel();

        // Publish multiple messages; all should be distributed across the channel pool
        for (int i = 0; i < 10; i++) {
            publisher.publish(message);
        }

        // Count total basicPublish calls across all channels in the pool
        int totalPublishes = 0;
        totalPublishes += Mockito.mockingDetails(channel1).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("basicPublish")).count();
        totalPublishes += Mockito.mockingDetails(channel2).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("basicPublish")).count();
        totalPublishes += Mockito.mockingDetails(channel3).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("basicPublish")).count();
        assertEquals(10, totalPublishes, "All 10 publishes should be distributed across channel pool");
    }

    // ---- Publisher Concurrency: Default Backward Compatibility ----

    /**
     * Verifies that the default publisherConcurrency is 1, preserving backward compatibility.
     * Only a single newChannel() call should occur during start().
     */
    @Test
    void testPublisherConcurrencyDefaultIsOne() throws Exception {
        Channel channel = Mockito.mock(Channel.class);
        val queueName = "queue-default-concurrency";
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-default");

        // Default publisherConcurrency should be 1
        assertEquals(1, actorConfig.getPublisherConcurrency());

        Mockito.doReturn(channel).when(connection).channel();
        Mockito.doReturn(null).when(channel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());

        val publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        publisher.start();

        // Only 1 channel should be created (default behavior preserved)
        verify(connection, times(1)).newChannel();
    }

    // ---- Publisher Channel Recovery: ShutdownListener Auto-Recovery ----

    /**
     * Verifies that when a publisher channel is shut down (e.g. broker-initiated close),
     * the ShutdownListener removes the dead channel from the pool and creates a replacement.
     * This ensures the channel pool self-heals without manual intervention.
     */
    @Test
    void testPublisherChannelRecoveryOnShutdown() throws Exception {
        Channel adminChannel = Mockito.mock(Channel.class);
        val queueName = "queue-recovery";
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-recovery");
        actorConfig.setPublisherConcurrency(1);

        // First call returns the original channel; second returns the replacement
        Channel originalChannel = Mockito.mock(Channel.class);
        Channel replacementChannel = Mockito.mock(Channel.class);
        Mockito.doReturn(originalChannel).doReturn(replacementChannel).when(connection).newChannel();
        Mockito.doReturn(adminChannel).when(connection).channel();
        Mockito.doReturn(null).when(adminChannel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());

        val publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        publisher.start();

        // Verify initial channel was created
        verify(connection, times(1)).newChannel();

        // Capture the ShutdownListener registered on the original channel
        val shutdownCaptor = ArgumentCaptor.forClass(ShutdownListener.class);
        verify(originalChannel).addShutdownListener(shutdownCaptor.capture());

        // Simulate a broker-initiated channel shutdown
        val shutdownSignal = new ShutdownSignalException(false, false, null, originalChannel);
        shutdownCaptor.getValue().shutdownCompleted(shutdownSignal);

        // Give CompletableFuture.runAsync time to execute the recovery
        Thread.sleep(500);

        // A replacement channel should have been created
        verify(connection, times(2)).newChannel();
        // Replacement channel should also have a ShutdownListener for future resilience
        verify(replacementChannel).addShutdownListener(ArgumentMatchers.any());
    }

    // ---- Publisher Concurrency: Stop/Cleanup ----

    /**
     * Verifies that stop() closes all open channels in the pool and clears the list.
     * Channels that are already closed should be skipped gracefully.
     */
    @Test
    void testPublisherStopClosesAllChannels() throws Exception {
        Channel adminChannel = Mockito.mock(Channel.class);
        val queueName = "queue-stop-test";
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-stop");
        actorConfig.setPublisherConcurrency(3);

        Channel ch1 = Mockito.mock(Channel.class);
        Channel ch2 = Mockito.mock(Channel.class);
        Channel ch3 = Mockito.mock(Channel.class);
        Mockito.doReturn(ch1).doReturn(ch2).doReturn(ch3).when(connection).newChannel();
        Mockito.doReturn(adminChannel).when(connection).channel();
        Mockito.doReturn(null).when(adminChannel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());

        // ch1 and ch3 are open; ch2 is already closed
        Mockito.doReturn(true).when(ch1).isOpen();
        Mockito.doReturn(false).when(ch2).isOpen();
        Mockito.doReturn(true).when(ch3).isOpen();

        val publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        publisher.start();
        publisher.stop();

        // Only open channels should be closed
        verify(ch1, times(1)).close();
        verify(ch2, times(0)).close();
        verify(ch3, times(1)).close();
    }

    // ---- Publisher Concurrency: Each Channel Gets a ShutdownListener ----

    /**
     * Verifies that every channel in the pool has a ShutdownListener registered,
     * ensuring all channels are resilient to broker-initiated shutdowns.
     */
    @Test
    void testAllPoolChannelsHaveShutdownListener() throws Exception {
        Channel adminChannel = Mockito.mock(Channel.class);
        val queueName = "queue-listeners";
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-listeners");
        actorConfig.setPublisherConcurrency(3);

        Channel ch1 = Mockito.mock(Channel.class);
        Channel ch2 = Mockito.mock(Channel.class);
        Channel ch3 = Mockito.mock(Channel.class);
        Mockito.doReturn(ch1).doReturn(ch2).doReturn(ch3).when(connection).newChannel();
        Mockito.doReturn(adminChannel).when(connection).channel();
        Mockito.doReturn(null).when(adminChannel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());

        val publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        publisher.start();

        // Every channel in the pool must have a ShutdownListener for auto-recovery
        verify(ch1).addShutdownListener(ArgumentMatchers.any());
        verify(ch2).addShutdownListener(ArgumentMatchers.any());
        verify(ch3).addShutdownListener(ArgumentMatchers.any());
    }

    // ---- Publisher Concurrency: Works With Sharded Queues ----

    /**
     * Verifies that publisherConcurrency works correctly alongside sharded queues.
     * The channel pool size should be determined by publisherConcurrency, not shardCount.
     */
    @Test
    void testPublisherConcurrencyWithShardedQueues() throws Exception {
        Channel adminChannel = Mockito.mock(Channel.class);
        val queueName = "queue-sharded-publish";
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-sharded-publish");
        actorConfig.setPublisherConcurrency(2);
        actorConfig.setShardCount(4);
        actorConfig.setConcurrency(4); // must be multiple of shardCount

        Channel ch1 = Mockito.mock(Channel.class);
        Channel ch2 = Mockito.mock(Channel.class);
        Mockito.doReturn(ch1).doReturn(ch2).when(connection).newChannel();
        Mockito.doReturn(adminChannel).when(connection).channel();
        Mockito.doReturn(null).when(adminChannel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());

        val publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        publisher.start();

        // Channel pool size should be publisherConcurrency (2), independent of shardCount (4)
        verify(connection, times(2)).newChannel();
    }

    // ---- Publisher Concurrency: Works With TTL Delayed Queues ----

    /**
     * Verifies that publisherConcurrency works correctly with TTL-delayed queues.
     * The TTL exchange setup and channel pool creation should coexist.
     */
    @Test
    void testPublisherConcurrencyWithTtlDelayedQueue() throws Exception {
        Channel adminChannel = Mockito.mock(Channel.class);
        val queueName = "queue-ttl-concurrent";
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-ttl-concurrent");
        actorConfig.setPublisherConcurrency(2);
        actorConfig.setDelayed(true);
        actorConfig.setDelayType(DelayType.TTL);

        Channel ch1 = Mockito.mock(Channel.class);
        Channel ch2 = Mockito.mock(Channel.class);
        Mockito.doReturn(ch1).doReturn(ch2).when(connection).newChannel();
        Mockito.doReturn(adminChannel).when(connection).channel();
        Mockito.doReturn(null).when(adminChannel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());

        val publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        publisher.start();

        // TTL exchange declarations should still work with multiple publisher channels
        verify(adminChannel, times(1)).exchangeDeclare(
                "test-exchange-ttl-concurrent_TTL",
                "direct",
                true
        );
        verify(adminChannel, times(1)).exchangeDeclare(
                "test-exchange-ttl-concurrent",
                "direct",
                true
        );

        // Channel pool size should still be publisherConcurrency (2)
        verify(connection, times(2)).newChannel();
    }

    // ---- Publisher Concurrency: Recovery Maintains Pool Size ----

    /**
     * Verifies that after a channel shutdown and recovery, the pool size is maintained.
     * In a pool of 2, if one channel dies and is replaced, the total newChannel() calls
     * should be 3 (2 initial + 1 replacement).
     */
    @Test
    void testPublisherRecoveryMaintainsPoolSize() throws Exception {
        Channel adminChannel = Mockito.mock(Channel.class);
        val queueName = "queue-pool-recovery";
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-pool-recovery");
        actorConfig.setPublisherConcurrency(2);

        Channel ch1 = Mockito.mock(Channel.class);
        Channel ch2 = Mockito.mock(Channel.class);
        Channel replacementCh = Mockito.mock(Channel.class);
        Mockito.doReturn(ch1).doReturn(ch2).doReturn(replacementCh).when(connection).newChannel();
        Mockito.doReturn(adminChannel).when(connection).channel();
        Mockito.doReturn(null).when(adminChannel).exchangeDeclare(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());

        val publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, objectMapper);
        publisher.start();

        // Verify initial pool of 2 channels
        verify(connection, times(2)).newChannel();

        // Kill ch1 via ShutdownListener
        val shutdownCaptor = ArgumentCaptor.forClass(ShutdownListener.class);
        verify(ch1).addShutdownListener(shutdownCaptor.capture());

        val shutdownSignal = new ShutdownSignalException(false, false, null, ch1);
        shutdownCaptor.getValue().shutdownCompleted(shutdownSignal);

        // Wait for async recovery
        Thread.sleep(500);

        // Total channels created: 2 initial + 1 replacement = 3
        verify(connection, times(3)).newChannel();
    }
}