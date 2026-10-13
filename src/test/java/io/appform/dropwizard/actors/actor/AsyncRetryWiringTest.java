package io.appform.dropwizard.actors.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import io.appform.dropwizard.actors.base.Handler;
import io.appform.dropwizard.actors.base.RandomShardIdCalculator;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.connectivity.strategy.DefaultConnectionStrategy;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import io.appform.dropwizard.actors.retry.RetryMode;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.dropwizard.util.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock-based wiring test: real {@link UnmanagedBaseActor} construction with async retry for the main
 * consumer and the sideline processor, wired against a mocked {@link RMQConnection}.
 */
class AsyncRetryWiringTest {

    private static final String NAME = "wiring-actor";
    private static final String PREFIX = "test.prefix";
    private static final String EXCHANGE = "wiring.exchange";
    private static final long WAIT_MS = 100L;
    private static final long CONFIRM_TIMEOUT_MS = 5000L;
    private static final String RETRY_SUFFIX = "_RETRY_" + WAIT_MS;

    private static final String QUEUE = NamingUtils.queueName(PREFIX, NAME);
    private static final String QUEUE_0 = NamingUtils.getShardedQueueName(QUEUE, 0);
    private static final String QUEUE_1 = NamingUtils.getShardedQueueName(QUEUE, 1);
    private static final String SIDELINE_QUEUE = NamingUtils.getSidelineProcessor(QUEUE);
    private static final String SIDELINE_EXCHANGE = NamingUtils.getSidelineProcessor(EXCHANGE);
    private static final String DLX = NamingUtils.getSideline(EXCHANGE);

    private final List<Channel> allChannels = new CopyOnWriteArrayList<>();
    private final List<Channel> channels = new CopyOnWriteArrayList<>();
    private RMQConnection connection;
    private ObjectMapper mapper;
    private byte[] body;

    public static class TestMessage {
        public String id = "m-1";
    }

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        connection = mock(RMQConnection.class);
        var topologyChannel = mock(Channel.class);
        allChannels.add(topologyChannel);
        when(connection.channel()).thenReturn(topologyChannel);
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.newChannel()).thenAnswer(invocation -> newChannelMock());
        // rmqOpts computes real queue arguments through the actual queue type visitor
        lenient().when(connection.rmqOpts(any(ActorConfig.class))).thenAnswer(invocation -> {
            var config = invocation.<ActorConfig>getArgument(0);
            return new java.util.HashMap<>(
                    config.getQueueType().handleConfig(new QueueTypeVisitorImpl(config)));
        });
        lenient().when(connection.rmqOpts(anyString(), any(ActorConfig.class))).thenAnswer(invocation -> {
            var deadLetterExchange = invocation.<String>getArgument(0);
            var config = invocation.<ActorConfig>getArgument(1);
            var arguments = new java.util.HashMap<String, Object>(
                    config.getQueueType().handleConfig(new QueueTypeVisitorImpl(config)));
            arguments.put("x-dead-letter-exchange", deadLetterExchange);
            return arguments;
        });
        // topology helpers delegate to the shared topology channel so queue/binding arguments can be inspected
        lenient().doAnswer(invocation -> {
            var queueName = invocation.<String>getArgument(0);
            var opts = invocation.<java.util.Map<String, Object>>getArgument(3);
            topologyChannel.queueDeclare(queueName, true, false, false, opts);
            topologyChannel.queueBind(queueName, invocation.getArgument(2), invocation.getArgument(1));
            return null;
        }).when(connection).ensure(anyString(), anyString(), anyString(), any(java.util.Map.class));
        lenient().doAnswer(invocation -> {
            var queueName = invocation.<String>getArgument(0);
            var opts = invocation.<java.util.Map<String, Object>>getArgument(2);
            topologyChannel.queueDeclare(queueName, true, false, false, opts);
            topologyChannel.queueBind(queueName, invocation.getArgument(1), queueName);
            return null;
        }).when(connection).ensure(anyString(), anyString(), any(java.util.Map.class));
        lenient().doAnswer(invocation -> {
            topologyChannel.queueBind(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(connection).addBinding(anyString(), anyString(), anyString());
        lenient().doAnswer(invocation -> {
            var topology = invocation.<Channel>getArgument(0);
            topology.queueDeclare(invocation.getArgument(1), true, false, false, invocation.getArgument(4));
            topology.queueBind(invocation.getArgument(1), invocation.getArgument(3), invocation.getArgument(2));
            return null;
        }).when(connection).ensure(any(Channel.class), anyString(), anyString(), anyString(), any(Map.class));
        lenient().doAnswer(invocation -> {
            var topology = invocation.<Channel>getArgument(0);
            topology.queueBind(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
            return null;
        }).when(connection).addBinding(any(Channel.class), anyString(), anyString(), anyString());
        body = mapper.writeValueAsBytes(new TestMessage());
    }

    private Channel newChannelMock() throws IOException {
        var channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        when(channel.basicConsume(anyString(), anyBoolean(), anyString(), any(Consumer.class)))
                .thenAnswer(consume -> "ctag-" + allChannels.size());
        allChannels.add(channel);
        channels.add(channel);
        return channel;
    }

    private CountLimitedFixedWaitRetryConfig asyncRetry(int maxAttempts) {
        var retry = new CountLimitedFixedWaitRetryConfig();
        retry.setRetryMode(RetryMode.ASYNC);
        retry.setMaxAttempts(maxAttempts);
        retry.setWaitTime(Duration.milliseconds(WAIT_MS));
        return retry;
    }

    private ActorConfig actorConfig(CountLimitedFixedWaitRetryConfig mainRetry, boolean lazy,
                                     Integer shardCount, SidelineProcessorConfig sideline) {
        var builder = ActorConfig.builder()
                .exchange(EXCHANGE)
                .prefix(PREFIX)
                .concurrency(shardCount == null ? 1 : 2)
                .prefetchCount(1)
                .lazyMode(lazy)
                .retryConfig(mainRetry)
                .consumer(ConsumerConfig.builder()
                        .connectionIsolationStrategy(new DefaultConnectionStrategy())
                        .tagPrefix("main")
                        .build());
        if (shardCount != null) {
            builder.shardCount(shardCount);
        }
        if (sideline != null) {
            builder.sidelineProcessorConfig(sideline);
        }
        return builder.build();
    }

    private SidelineProcessorConfig sidelineConfig() {
        return SidelineProcessorConfig.builder()
                .concurrency(1)
                .retryConfig(asyncRetry(3))
                .consumerConfig(ConsumerConfig.builder()
                        .connectionIsolationStrategy(new DefaultConnectionStrategy())
                        .tagPrefix("sideline")
                        .build())
                .build();
    }

    private UnmanagedBaseActor<TestMessage> startActor(ActorConfig config) throws Exception {
        var actor = new UnmanagedBaseActor<>(NAME, config, connection, connection, connection, mapper,
                new RandomShardIdCalculator<>(config), new RetryStrategyFactory(),
                new ExceptionHandlingFactory(), TestMessage.class,
                (message, metadata) -> {
                    throw new IOException("boom");
                },
                (message, metadata) -> {
                    throw new IOException("sideline boom");
                },
                (message, metadata) -> true,
                (message, metadata) -> true,
                error -> false);
        actor.start();
        return actor;
    }

    private record Registration(Channel channel, String queue, String consumerTag, Handler<TestMessage> handler) {
        private String tag() {
            return handler.getTag();
        }
    }

    private Registration consumerOn(String queue) {
        for (Channel channel : channels) {
            for (Invocation invocation : mockingDetails(channel).getInvocations()) {
                if (invocation.getMethod().getName().equals("basicConsume")
                        && queue.equals(invocation.getArgument(0))) {
                    return new Registration(channel, queue, invocation.getArgument(2), invocation.getArgument(3));
                }
            }
        }
        throw new AssertionError("No consumer registered on queue " + queue);
    }

    private void deliverFailingMessage(Registration registration, long deliveryTag) throws IOException {
        registration.handler().handleDelivery("ctag",
                new Envelope(deliveryTag, false, EXCHANGE, registration.queue()),
                new AMQP.BasicProperties(), body);
    }

    private List<Invocation> callsNamed(String method) {
        return allChannels.stream()
                .flatMap(channel -> mockingDetails(channel).getInvocations().stream())
                .filter(invocation -> invocation.getMethod().getName().equals(method))
                .toList();
    }

    private List<Invocation> withFirstArg(List<Invocation> invocations, Object argument) {
        return invocations.stream()
                .filter(invocation -> argument.equals(invocation.getArgument(0)))
                .toList();
    }

    private Invocation single(List<Invocation> invocations) {
        assertEquals(1, invocations.size(), () -> "Expected exactly one invocation but found " + invocations);
        return invocations.get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> declaredArguments(Invocation invocation) {
        return (Map<String, Object>) invocation.getArgument(4);
    }

    private Channel publishChannelOf(String exchange) {
        return channels.stream()
                .filter(channel -> mockingDetails(channel).getInvocations().stream()
                        .anyMatch(invocation -> invocation.getMethod().getName().equals("basicPublish")
                                && exchange.equals(invocation.getArgument(0))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No publish channel for exchange " + exchange));
    }

    private ShutdownListener shutdownListenerOn(Channel channel) {
        return mockingDetails(channel).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("addShutdownListener"))
                .map(invocation -> (ShutdownListener) invocation.getArgument(0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No shutdown listener on channel"));
    }

    @Test
    void failingDeliveriesAreHandedOffToLazyTtlRetryQueuesForMainAndSidelineConsumers() throws Exception {
        var actor = startActor(actorConfig(asyncRetry(3), true, null, sidelineConfig()));

        var main = consumerOn(QUEUE);
        var sideline = consumerOn(SIDELINE_QUEUE);
        assertEquals("main_1", main.consumerTag());
        assertEquals("sideline_1", sideline.consumerTag());

        // the main queue itself is declared lazy with the sideline dead-letter exchange
        var mainDeclare = single(withFirstArg(callsNamed("queueDeclare"), QUEUE));
        assertEquals(Boolean.TRUE, mainDeclare.getArgument(1));
        assertEquals(Boolean.FALSE, mainDeclare.getArgument(2));
        var mainArgs = declaredArguments(mainDeclare);
        assertEquals("lazy", mainArgs.get("x-queue-mode"));
        assertEquals(DLX, mainArgs.get("x-dead-letter-exchange"));

        // main consumer: failing delivery is handed off to a lazy TTL retry queue and acked
        deliverFailingMessage(main, 11L);
        verify(main.channel()).basicAck(11L, false);
        verify(main.channel(), never()).basicReject(anyLong(), anyBoolean());

        var retryQueue = QUEUE + RETRY_SUFFIX;
        var retryExchange = EXCHANGE + RETRY_SUFFIX;
        var retryDeclare = single(withFirstArg(callsNamed("queueDeclare"), retryQueue));
        assertEquals(Boolean.TRUE, retryDeclare.getArgument(1));
        assertEquals(Boolean.FALSE, retryDeclare.getArgument(3));
        var retryArgs = declaredArguments(retryDeclare);
        assertEquals(WAIT_MS, ((Number) retryArgs.get("x-message-ttl")).longValue());
        assertEquals(EXCHANGE, retryArgs.get("x-dead-letter-exchange"));
        assertEquals("lazy", retryArgs.get("x-queue-mode"));
        assertEquals("classic", retryArgs.get("x-queue-type"));
        assertEquals(5, retryArgs.size());
        var retryExchangeDeclare = single(withFirstArg(callsNamed("exchangeDeclare"), retryExchange));
        assertEquals("direct", retryExchangeDeclare.getArgument(1));
        assertEquals(Boolean.TRUE, retryExchangeDeclare.getArgument(2));
        var retryBinding = single(withFirstArg(callsNamed("queueBind"), retryQueue));
        assertEquals(retryExchange, retryBinding.getArgument(1));
        assertEquals(QUEUE, retryBinding.getArgument(2));

        var publish = single(withFirstArg(callsNamed("basicPublish"), retryExchange));
        assertEquals(QUEUE, publish.getArgument(1));
        assertEquals(Boolean.TRUE, publish.getArgument(2));
        var properties = (AMQP.BasicProperties) publish.getArgument(3);
        assertNull(properties.getExpiration());
        assertEquals(QUEUE, properties.getHeaders().get("x-actor-retry-queue"));
        assertEquals(2L, properties.getHeaders().get("x-actor-retry-attempt"));
        var startedAt = ((Number) properties.getHeaders().get("x-actor-retry-started-at")).longValue();
        assertTrue(startedAt > 0 && startedAt <= System.currentTimeMillis());
        assertSame(body, publish.getArgument(4));
        verify(publishChannelOf(retryExchange)).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);

        // sideline processor consumer: exact sideline destinations for its own async retry
        deliverFailingMessage(sideline, 12L);
        verify(sideline.channel()).basicAck(12L, false);

        var sidelineRetryQueue = SIDELINE_QUEUE + RETRY_SUFFIX;
        var sidelineRetryExchange = SIDELINE_EXCHANGE + RETRY_SUFFIX;
        var sidelineRetryArgs = declaredArguments(
                single(withFirstArg(callsNamed("queueDeclare"), sidelineRetryQueue)));
        assertEquals(WAIT_MS, ((Number) sidelineRetryArgs.get("x-message-ttl")).longValue());
        assertEquals(SIDELINE_EXCHANGE, sidelineRetryArgs.get("x-dead-letter-exchange"));
        var sidelinePublish = single(withFirstArg(callsNamed("basicPublish"), sidelineRetryExchange));
        assertEquals(SIDELINE_QUEUE, sidelinePublish.getArgument(1));
        verify(publishChannelOf(sidelineRetryExchange)).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);

        actor.stop();
    }

    @Test
    void maxAttemptsOfOneRejectsOnFirstFailureWithoutSchedulingAnyRetryQueue() throws Exception {
        startActor(actorConfig(asyncRetry(1), false, null, null));

        var main = consumerOn(QUEUE);
        deliverFailingMessage(main, 21L);

        verify(main.channel()).basicReject(21L, false);
        verify(main.channel(), never()).basicAck(anyLong(), anyBoolean());
        assertTrue(callsNamed("confirmSelect").isEmpty());
        assertTrue(withFirstArg(callsNamed("queueDeclare"), QUEUE + RETRY_SUFFIX).isEmpty());
        assertTrue(withFirstArg(callsNamed("exchangeDeclare"), EXCHANGE + RETRY_SUFFIX).isEmpty());
        assertTrue(withFirstArg(callsNamed("basicPublish"), EXCHANGE + RETRY_SUFFIX).isEmpty());
    }

    @Test
    void shardedActorsRetryIntoPerShardDestinations() throws Exception {
        startActor(actorConfig(asyncRetry(2), false, 2, null));

        var shardZero = consumerOn(QUEUE_0);
        var shardOne = consumerOn(QUEUE_1);
        deliverFailingMessage(shardOne, 31L);

        verify(shardOne.channel()).basicAck(31L, false);
        verify(shardZero.channel(), never()).basicAck(anyLong(), anyBoolean());

        var retryQueue = QUEUE + RETRY_SUFFIX;
        var retryExchange = EXCHANGE + RETRY_SUFFIX;
        var bindings = withFirstArg(callsNamed("queueBind"), retryQueue);
        assertEquals(2, bindings.size());
        var boundKeys = bindings.stream()
                .map(invocation -> (String) invocation.getArgument(2))
                .collect(Collectors.toSet());
        assertEquals(Set.of(QUEUE_0, QUEUE_1), boundKeys);
        assertEquals(QUEUE_1, single(withFirstArg(callsNamed("basicPublish"), retryExchange)).getArgument(1));
    }

    @Test
    void stopReleasesConsumerPublisherAndSchedulerChannelsAndIgnoresApplicationShutdown() throws Exception {
        var actor = startActor(actorConfig(asyncRetry(3), false, null, sidelineConfig()));

        var main = consumerOn(QUEUE);
        var sideline = consumerOn(SIDELINE_QUEUE);
        deliverFailingMessage(main, 41L);
        var schedulerChannel = publishChannelOf(EXCHANGE + RETRY_SUFFIX);
        var publisherChannel = channels.get(0);
        var consumersBefore = callsNamed("basicConsume").size();
        var channelsBefore = allChannels.size();

        actor.stop();

        verify(main.channel()).basicCancel(main.tag());
        verify(main.channel()).close();
        verify(sideline.channel()).basicCancel(sideline.tag());
        verify(sideline.channel()).close();
        verify(publisherChannel).close();
        verify(schedulerChannel).close();

        // an application-initiated shutdown signal must not re-create consumer channels
        shutdownListenerOn(main.channel())
                .shutdownCompleted(new ShutdownSignalException(true, true, null, null));
        assertEquals(consumersBefore, callsNamed("basicConsume").size());
        assertEquals(channelsBefore, allChannels.size());
    }
}
