package io.appform.dropwizard.actors;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.actor.ExchangeType;
import io.appform.dropwizard.actors.base.RandomShardIdCalculator;
import io.appform.dropwizard.actors.base.RoutingKeyResolver;
import io.appform.dropwizard.actors.base.ShardIdCalculator;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.config.MetricConfig;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.config.RmqManagementConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.connectivity.actor.RabbitMQBundleTestAppConfiguration;
import io.appform.dropwizard.actors.observers.ThreadLocalObserver;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import java.util.ArrayList;
import java.util.Collections;
import lombok.val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ExchangeTypesTest {

    private RMQConnection connection;
    private Channel channel;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() throws Exception {
        val config = RMQConfig.builder()
                .brokers(new ArrayList<>())
                .userName("")
                .threadPoolSize(1)
                .password("")
                .secure(false)
                .startupGracePeriodSeconds(1)
                .metricConfig(MetricConfig.builder().enabledForAll(true).build())
                .build();
        val actorBundleImpl = new RabbitmqActorBundle<RabbitMQBundleTestAppConfiguration>() {
            @Override
            protected TtlConfig ttlConfig() {
                return TtlConfig.builder().build();
            }

            @Override
            protected RMQConfig getConfig(RabbitMQBundleTestAppConfiguration c) {
                return config;
            }
        };
        this.connection = Mockito.mock(RMQConnection.class);
        this.channel = Mockito.mock(Channel.class);
        this.objectMapper = new ObjectMapper();
        val environment = Mockito.mock(Environment.class);
        val lifecycle = Mockito.mock(LifecycleEnvironment.class);
        Mockito.doReturn(new MetricRegistry()).when(environment).metrics();
        Mockito.doReturn(lifecycle).when(environment).lifecycle();
        Mockito.doNothing().when(lifecycle).manage(ArgumentMatchers.any(ConnectionRegistry.class));
        actorBundleImpl.registerObserver(new ThreadLocalObserver(null));
        actorBundleImpl.run(new RabbitMQBundleTestAppConfiguration(), environment);

        Mockito.doReturn(actorBundleImpl.getConnectionRegistry().getRootObserver()).when(connection).getRootObserver();
        Mockito.doReturn(channel).when(connection).newChannel();
        Mockito.doReturn(channel).when(connection).channel();
    }

    @Test
    void testDirectExchangeDeclaredByDefault() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("direct-exchange");
        val queueName = NamingUtils.queueName(actorConfig.getPrefix(), "direct-queue");
        val publisher = newPublisher("direct-queue", actorConfig, null);
        publisher.start();

        verify(channel, times(1)).exchangeDeclare("direct-exchange", "direct", true);
        // Backward compatibility: DIRECT must NOT pin x-dead-letter-routing-key (would break redeclare of
        // pre-existing queues). It must use the original 2-arg rmqOpts (dlx only).
        verify(connection, times(1)).rmqOpts(
                ArgumentMatchers.eq(NamingUtils.getSideline("direct-exchange")),
                ArgumentMatchers.eq(actorConfig));
        verify(connection, org.mockito.Mockito.never()).rmqOpts(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(queueName),
                ArgumentMatchers.eq(actorConfig));
    }

    @Test
    void testTopicExchangeDeclaredWithBindingKeys() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("topic-exchange");
        actorConfig.setExchangeType(ExchangeType.TOPIC);
        actorConfig.setBindingKeys(List.of("orders.*", "orders.created"));
        val queueName = NamingUtils.queueName(actorConfig.getPrefix(), "topic-queue");
        val publisher = newPublisher("topic-queue", actorConfig, message -> "orders.created");
        publisher.start();

        verify(channel, times(1)).exchangeDeclare("topic-exchange", "topic", true);
        verify(connection, times(1)).ensureWithBindingKeys(
                ArgumentMatchers.eq(queueName),
                ArgumentMatchers.eq("topic-exchange"),
                ArgumentMatchers.eq(List.of("orders.*", "orders.created")),
                ArgumentMatchers.any());
        // Dead-letter routing key must be pinned to the queue name so dead-lettered messages
        // route to the sideline queue on the direct _SIDELINE exchange regardless of the topic routing key.
        verify(connection, times(1)).rmqOpts(
                ArgumentMatchers.eq(NamingUtils.getSideline("topic-exchange")),
                ArgumentMatchers.eq(queueName),
                ArgumentMatchers.eq(actorConfig));
    }

    @Test
    void testTopicWithoutResolverFails() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("topic-exchange");
        actorConfig.setExchangeType(ExchangeType.TOPIC);
        val publisher = newPublisher("topic-queue", actorConfig, null);
        Assertions.assertThrows(IllegalStateException.class, publisher::start);
    }

    @Test
    void testTtlDelayWithTopicFails() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("topic-exchange");
        actorConfig.setExchangeType(ExchangeType.TOPIC);
        actorConfig.setDelayed(true);
        actorConfig.setDelayType(DelayType.TTL);
        val publisher = newPublisher("topic-queue", actorConfig, message -> "rk");
        Assertions.assertThrows(IllegalStateException.class, publisher::start);
    }

    @Test
    void testTtlDelayWithFanoutFails() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("fanout-exchange");
        actorConfig.setExchangeType(ExchangeType.FANOUT);
        actorConfig.setDelayed(true);
        actorConfig.setDelayType(DelayType.TTL);
        val publisher = newPublisher("fanout-queue", actorConfig, null);
        Assertions.assertThrows(IllegalStateException.class, publisher::start);
    }

    @Test
    void testFanoutExchangeDeclaredWithEmptyBindingKey() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("fanout-exchange");
        actorConfig.setExchangeType(ExchangeType.FANOUT);
        val queueName = NamingUtils.queueName(actorConfig.getPrefix(), "fanout-queue");
        val publisher = newPublisher("fanout-queue", actorConfig, null);
        publisher.start();

        verify(channel, times(1)).exchangeDeclare("fanout-exchange", "fanout", true);
        verify(connection, times(1)).ensureWithBindingKeys(
                ArgumentMatchers.eq(queueName),
                ArgumentMatchers.eq("fanout-exchange"),
                ArgumentMatchers.eq(List.of("")),
                ArgumentMatchers.any());
    }

    @Test
    void testDelayedTopicExchangeUsesTopicDelayType() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("delayed-topic");
        actorConfig.setExchangeType(ExchangeType.TOPIC);
        actorConfig.setDelayed(true);
        actorConfig.setDelayType(DelayType.DELAYED);
        val publisher = newPublisher("delayed-topic-queue", actorConfig, message -> "rk");
        publisher.start();

        verify(channel, times(1)).exchangeDeclare(
                ArgumentMatchers.eq("delayed-topic"),
                ArgumentMatchers.eq("x-delayed-message"),
                ArgumentMatchers.eq(true),
                ArgumentMatchers.eq(false),
                ArgumentMatchers.eq(ImmutableMap.of("x-delayed-type", "topic")));
    }

    @Test
    void testSidelineAlwaysDirect() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("topic-exchange");
        actorConfig.setExchangeType(ExchangeType.TOPIC);
        val publisher = newPublisher("topic-queue", actorConfig, message -> "rk");
        publisher.start();

        verify(channel, times(1))
                .exchangeDeclare(NamingUtils.getSideline("topic-exchange"), "direct", true);
    }

    // ---------------------------------------------------------------------------------------------
    // Publish-time routing key resolution: this is what actually decides which queue(s) a message
    // reaches at runtime, and differs per exchange type.
    // ---------------------------------------------------------------------------------------------

    @Test
    void testDirectPublishUsesQueueNameAsRoutingKey() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("direct-exchange");
        val queueName = NamingUtils.queueName(actorConfig.getPrefix(), "direct-queue");
        val publisher = newPublisher("direct-queue", actorConfig, null);
        publisher.start();

        publisher.publish(Collections.singletonMap("k", "v"));

        // DIRECT: routing key == queue name so the direct exchange routes to exactly that queue.
        verify(channel, times(1)).basicPublish(
                eq("direct-exchange"), eq(queueName), any(), any(byte[].class));
    }

    @Test
    void testDirectShardedPublishUsesShardedQueueNameAsRoutingKey() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("direct-exchange");
        actorConfig.setShardCount(4);
        actorConfig.setConcurrency(4);
        val queueName = NamingUtils.queueName(actorConfig.getPrefix(), "sharded-queue");
        // Deterministic shard so we can assert the exact routing key.
        final ShardIdCalculator<Object> fixedShard = message -> 2;
        val publisher = new UnmanagedPublisher<>(queueName, actorConfig, fixedShard, null, connection, objectMapper);
        publisher.start();

        publisher.publish(Collections.singletonMap("k", "v"));

        // DIRECT + sharded: routing key == <queue>_<shardId> so it lands on the chosen shard queue.
        verify(channel, times(1)).basicPublish(
                eq("direct-exchange"),
                eq(NamingUtils.getShardedQueueName(queueName, 2)),
                any(),
                any(byte[].class));
    }

    @Test
    void testTopicPublishUsesResolvedRoutingKey() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("topic-exchange");
        actorConfig.setExchangeType(ExchangeType.TOPIC);
        val publisher = newPublisher("topic-queue", actorConfig, message -> "orders.created.v2");
        publisher.start();

        publisher.publish(Collections.singletonMap("k", "v"));

        // TOPIC: routing key comes from the RoutingKeyResolver so the broker can pattern-match bindings.
        verify(channel, times(1)).basicPublish(
                eq("topic-exchange"), eq("orders.created.v2"), any(), any(byte[].class));
    }

    @Test
    void testFanoutPublishUsesEmptyRoutingKey() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("fanout-exchange");
        actorConfig.setExchangeType(ExchangeType.FANOUT);
        val publisher = newPublisher("fanout-queue", actorConfig, null);
        publisher.start();

        publisher.publish(Collections.singletonMap("k", "v"));

        // FANOUT: routing key is ignored by the broker; publisher sends empty string.
        verify(channel, times(1)).basicPublish(
                eq("fanout-exchange"), eq(""), any(), any(byte[].class));
    }

    // ---------------------------------------------------------------------------------------------
    // Config validation (@ValidationMethod). These guardrails are enforced at config-load time and
    // document the constraints listed as limitations.
    // ---------------------------------------------------------------------------------------------

    @Test
    void testValidationRejectsBindingKeysOnDirect() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("direct-exchange");
        actorConfig.setExchangeType(ExchangeType.DIRECT);
        actorConfig.setBindingKeys(List.of("orders.*"));
        Assertions.assertFalse(actorConfig.isValidBindingKeys());
    }

    @Test
    void testValidationRejectsBindingKeysOnFanout() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("fanout-exchange");
        actorConfig.setExchangeType(ExchangeType.FANOUT);
        actorConfig.setBindingKeys(List.of("orders.*"));
        Assertions.assertFalse(actorConfig.isValidBindingKeys());
    }

    @Test
    void testValidationAllowsBindingKeysOnTopic() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("topic-exchange");
        actorConfig.setExchangeType(ExchangeType.TOPIC);
        actorConfig.setBindingKeys(List.of("orders.*"));
        Assertions.assertTrue(actorConfig.isValidBindingKeys());
    }

    @Test
    void testValidationRejectsFanoutWithSharding() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("fanout-exchange");
        actorConfig.setExchangeType(ExchangeType.FANOUT);
        actorConfig.setShardCount(2);
        Assertions.assertFalse(actorConfig.isValidFanoutSharding());
    }

    @Test
    void testValidationRejectsTopicWithSharding() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("topic-exchange");
        actorConfig.setExchangeType(ExchangeType.TOPIC);
        actorConfig.setShardCount(2);
        Assertions.assertFalse(actorConfig.isValidTopicSharding());
    }

    @Test
    void testValidationRejectsTtlDelayOnNonDirect() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("topic-exchange");
        actorConfig.setExchangeType(ExchangeType.TOPIC);
        actorConfig.setDelayed(true);
        actorConfig.setDelayType(DelayType.TTL);
        Assertions.assertFalse(actorConfig.isValidTtlDelayExchangeType());
    }

    @Test
    void testValidationAllowsTtlDelayOnDirect() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("direct-exchange");
        actorConfig.setExchangeType(ExchangeType.DIRECT);
        actorConfig.setDelayed(true);
        actorConfig.setDelayType(DelayType.TTL);
        Assertions.assertTrue(actorConfig.isValidTtlDelayExchangeType());
    }

    @Test
    void testValidationDefaultsAreValid() {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("direct-exchange");
        Assertions.assertTrue(actorConfig.isValidBindingKeys());
        Assertions.assertTrue(actorConfig.isValidFanoutSharding());
        Assertions.assertTrue(actorConfig.isValidTopicSharding());
        Assertions.assertTrue(actorConfig.isValidTtlDelayExchangeType());
    }

    @Test
    void testDirectSkipsManagementApiChecksEvenWhenEnabled() throws Exception {
        // Management API enabled in strict mode but pointing at an unreachable port: any Management-API call
        // would throw. DIRECT must keep its historical behaviour and never call it, so start() succeeds.
        final RMQConfig rmqConfig = new RMQConfig();
        rmqConfig.setBrokers(new ArrayList<>(List.of(new Broker("localhost", 5672))));
        rmqConfig.setUserName("guest");
        rmqConfig.setPassword("guest");
        rmqConfig.setManagementConfig(RmqManagementConfig.builder()
                .enabled(true)
                .port(1)
                .failOnManagementUnavailable(true)
                .build());
        Mockito.doReturn(rmqConfig).when(connection).getConfig();

        val actorConfig = new ActorConfig();
        actorConfig.setExchange("direct-exchange");
        val publisher = newPublisher("direct-queue", actorConfig, null);

        Assertions.assertDoesNotThrow(publisher::start);
        // DIRECT uses the original (pre-exchange-types) ensure(queue, exchange, opts) call, i.e. a single
        // binding keyed by the queue name - never the binding-keys path.
        verify(connection, times(1)).ensure(
                eq(NamingUtils.queueName(actorConfig.getPrefix(), "direct-queue")), eq("direct-exchange"), any());
        verify(connection, Mockito.never()).ensureWithBindingKeys(any(), any(), any(), any());
    }

    @Test
    void testShardedDirectUsesOriginalEnsurePerShard() throws Exception {
        val actorConfig = new ActorConfig();
        actorConfig.setExchange("direct-exchange");
        actorConfig.setShardCount(3);
        actorConfig.setConcurrency(3);
        val queueName = NamingUtils.queueName(actorConfig.getPrefix(), "sharded-queue");
        val publisher = newPublisher("sharded-queue", actorConfig, null);
        publisher.start();

        for (int shardId = 0; shardId < 3; shardId++) {
            verify(connection, times(1)).ensure(
                    eq(NamingUtils.getShardedQueueName(queueName, shardId)), eq("direct-exchange"), any());
        }
        verify(connection, Mockito.never()).ensureWithBindingKeys(any(), any(), any(), any());
    }

    @Test
    void testFanoutRunsManagementApiCheckWhenEnabled() {
        // Same unreachable, strict Management API: FANOUT does run the stale-exchange check, so start() fails.
        final RMQConfig rmqConfig = new RMQConfig();
        rmqConfig.setBrokers(new ArrayList<>(List.of(new Broker("localhost", 5672))));
        rmqConfig.setUserName("guest");
        rmqConfig.setPassword("guest");
        rmqConfig.setManagementConfig(RmqManagementConfig.builder()
                .enabled(true)
                .port(1)
                .failOnManagementUnavailable(true)
                .build());
        Mockito.doReturn(rmqConfig).when(connection).getConfig();

        val actorConfig = new ActorConfig();
        actorConfig.setExchange("fanout-exchange");
        actorConfig.setExchangeType(ExchangeType.FANOUT);
        val publisher = newPublisher("fanout-queue", actorConfig, null);

        Assertions.assertThrows(IllegalStateException.class, publisher::start);
    }

    // ---------------------------------------------------------------------------------------------
    // First-creation race (TOPIC, Management API strict). The queue looks new (no bindings), so we bind
    // our configured keys and re-read. If a concurrent creator bound divergent keys in the meantime, the
    // re-read is a superset of our configured keys and startup must fail. A MockWebServer stands in for the
    // Management API: the publisher derives its base URL from the broker host + managementConfig.port, so we
    // point both at the mock and serve the three reads (stale-exchange, first-creation, re-read) in FIFO order.
    // ---------------------------------------------------------------------------------------------

    @Test
    void testTopicFirstCreationRaceDivergentKeysFails() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            stubConnectionConfig(server);

            // #1 stale-exchange read (verifyNoStaleExchangeBindings): no stale exchange bindings.
            server.enqueue(jsonResponse("[]"));
            // #2 first-creation read (fetchBindingKeys): queue has no bindings yet -> we believe we are first.
            server.enqueue(jsonResponse("[]"));
            // #3 re-read after our bind: a concurrent creator added a divergent key -> superset of ours.
            server.enqueue(jsonResponse(bindings("topic-exchange", "order.*", "order.created")));

            val actorConfig = new ActorConfig();
            actorConfig.setExchange("topic-exchange");
            actorConfig.setExchangeType(ExchangeType.TOPIC);
            actorConfig.setBindingKeys(List.of("order.*"));
            val publisher = newPublisher("topic-queue", actorConfig, message -> "order.created");

            val ex = Assertions.assertThrows(IllegalStateException.class, publisher::start);
            Assertions.assertTrue(ex.getMessage().contains("mismatch after first-creation bind"),
                    "unexpected message: " + ex.getMessage());
            Assertions.assertEquals(3, server.getRequestCount());
        }
    }

    @Test
    void testTopicFirstCreationRaceMatchingKeysSucceeds() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            stubConnectionConfig(server);

            server.enqueue(jsonResponse("[]"));
            server.enqueue(jsonResponse("[]"));
            // Re-read agrees exactly with our configured keys -> no divergent creator -> start() succeeds.
            server.enqueue(jsonResponse(bindings("topic-exchange", "order.*")));

            val actorConfig = new ActorConfig();
            actorConfig.setExchange("topic-exchange");
            actorConfig.setExchangeType(ExchangeType.TOPIC);
            actorConfig.setBindingKeys(List.of("order.*"));
            val publisher = newPublisher("topic-queue", actorConfig, message -> "order.created");

            Assertions.assertDoesNotThrow(publisher::start);
            Assertions.assertEquals(3, server.getRequestCount());
        }
    }

    private void stubConnectionConfig(final MockWebServer server) {
        final RMQConfig rmqConfig = new RMQConfig();
        rmqConfig.setBrokers(new ArrayList<>(List.of(new Broker(server.getHostName(), 5672))));
        rmqConfig.setUserName("guest");
        rmqConfig.setPassword("guest");
        rmqConfig.setManagementConfig(RmqManagementConfig.builder()
                .enabled(true)
                .port(server.getPort())
                .failOnManagementUnavailable(true)
                .build());
        Mockito.doReturn(rmqConfig).when(connection).getConfig();
    }

    private static MockResponse jsonResponse(final String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static String bindings(final String source, final String... routingKeys) {
        val sb = new StringBuilder("[");
        for (int i = 0; i < routingKeys.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(
                    "{\"source\":\"%s\",\"vhost\":\"/\",\"destination\":\"q\","
                            + "\"destination_type\":\"queue\",\"routing_key\":\"%s\"}",
                    source, routingKeys[i]));
        }
        return sb.append(']').toString();
    }

    private UnmanagedPublisher<Object> newPublisher(final String name,
                                                    final ActorConfig actorConfig,
                                                    final RoutingKeyResolver<Object> resolver) {
        return new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), name), actorConfig,
                new RandomShardIdCalculator<>(actorConfig), resolver, connection,
                objectMapper);
    }
}