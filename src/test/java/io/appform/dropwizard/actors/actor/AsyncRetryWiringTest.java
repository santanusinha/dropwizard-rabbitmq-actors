package io.appform.dropwizard.actors.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.appform.dropwizard.actors.retry.WaitMode;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.dropwizard.util.Duration;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

/**
 * Mock-based wiring test: real {@link UnmanagedBaseActor} construction with async retry for the main
 * consumer and the sideline processor, wired against a mocked {@link RMQConnection}.
 */
class AsyncRetryWiringTest {

    private static final String NAME = "wiring-actor";
    private static final String PREFIX = "test.prefix";
    private static final String EXCHANGE = "wiring.exchange";
    private static final long WAIT_MS = 100L;
    private static final long CONFIRM_TIMEOUT_MS = RetryStrategyFactory.DEFAULT_CONFIRM_TIMEOUT_MS;

    private static final String QUEUE = NamingUtils.queueName(PREFIX, NAME);
    private static final String QUEUE_0 = NamingUtils.getShardedQueueName(QUEUE, 0);
    private static final String QUEUE_1 = NamingUtils.getShardedQueueName(QUEUE, 1);
    private static final String SIDELINE_QUEUE = NamingUtils.getSidelineProcessor(QUEUE);
    private static final String SIDELINE_EXCHANGE = NamingUtils.getSidelineProcessor(EXCHANGE);
    private static final String DLX = NamingUtils.getSideline(EXCHANGE);
    private static final String WAITING_QUEUE =
            NamingUtils.prefixWithNamespace(EXCHANGE + "_RETRY_classic_" + WAIT_MS);
    private static final String SIDELINE_WAITING_QUEUE =
            NamingUtils.prefixWithNamespace(SIDELINE_EXCHANGE + "_RETRY_classic_" + WAIT_MS);

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
            return new HashMap<>(
                    config.getQueueType().handleConfig(new QueueTypeVisitorImpl(config)));
        });
        lenient().when(connection.rmqOpts(anyString(), any(ActorConfig.class))).thenAnswer(invocation -> {
            var deadLetterExchange = invocation.<String>getArgument(0);
            var config = invocation.<ActorConfig>getArgument(1);
            var arguments = new HashMap<String, Object>(
                    config.getQueueType().handleConfig(new QueueTypeVisitorImpl(config)));
            arguments.put("x-dead-letter-exchange", deadLetterExchange);
            return arguments;
        });
        // topology helpers delegate to the shared topology channel so queue/binding arguments can be inspected
        lenient().doAnswer(invocation -> {
            var queueName = invocation.<String>getArgument(0);
            var opts = invocation.<Map<String, Object>>getArgument(3);
            topologyChannel.queueDeclare(queueName, true, false, false, opts);
            topologyChannel.queueBind(queueName, invocation.getArgument(2), invocation.getArgument(1));
            return null;
        }).when(connection).ensure(anyString(), anyString(), anyString(), any(Map.class));
        lenient().doAnswer(invocation -> {
            var queueName = invocation.<String>getArgument(0);
            var opts = invocation.<Map<String, Object>>getArgument(2);
            topologyChannel.queueDeclare(queueName, true, false, false, opts);
            topologyChannel.queueBind(queueName, invocation.getArgument(1), queueName);
            return null;
        }).when(connection).ensure(anyString(), anyString(), any(Map.class));
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
        retry.setWaitMode(WaitMode.ASYNC);
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

    private Channel publishChannelOf(String exchange, String routingKey) {
        return channels.stream()
                .filter(channel -> mockingDetails(channel).getInvocations().stream()
                        .anyMatch(invocation -> invocation.getMethod().getName().equals("basicPublish")
                                && exchange.equals(invocation.getArgument(0))
                                && routingKey.equals(invocation.getArgument(1))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No publish channel for " + exchange + " -> " + routingKey));
    }

    private ShutdownListener shutdownListenerOn(Channel channel) {
        return mockingDetails(channel).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("addShutdownListener"))
                .map(invocation -> (ShutdownListener) invocation.getArgument(0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No shutdown listener on channel"));
    }

    @Test
    void mainAndSidelineConsumersRetryThroughWaitingQueuesOfTheirOwnExchange() throws Exception {
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

        var waitingDeclare = single(withFirstArg(callsNamed("queueDeclare"), WAITING_QUEUE));
        assertEquals(Boolean.TRUE, waitingDeclare.getArgument(1));
        assertEquals(Map.of("x-queue-type", "classic", "x-message-ttl", WAIT_MS, "x-dead-letter-exchange", EXCHANGE),
                declaredArguments(waitingDeclare));
        assertEquals(Map.of("x-queue-type", "classic", "x-message-ttl", WAIT_MS,
                        "x-dead-letter-exchange", SIDELINE_EXCHANGE),
                declaredArguments(single(withFirstArg(callsNamed("queueDeclare"), SIDELINE_WAITING_QUEUE))));
        assertEquals("fanout", single(withFirstArg(callsNamed("exchangeDeclare"), WAITING_QUEUE)).getArgument(1));
        assertEquals(WAITING_QUEUE, single(withFirstArg(callsNamed("queueBind"), WAITING_QUEUE)).getArgument(1));

        deliverFailingMessage(main, 11L);
        verify(main.channel()).basicAck(11L, false);
        verify(main.channel(), never()).basicReject(anyLong(), anyBoolean());

        var publish = single(withFirstArg(callsNamed("basicPublish"), WAITING_QUEUE));
        assertEquals(QUEUE, publish.getArgument(1));
        assertEquals(Boolean.TRUE, publish.getArgument(2));
        var properties = (AMQP.BasicProperties) publish.getArgument(3);
        assertNull(properties.getExpiration());
        assertEquals(QUEUE, properties.getHeaders().get("x-actor-retry-queue"));
        assertEquals(2L, properties.getHeaders().get("x-actor-retry-attempt"));
        var startedAt = ((Number) properties.getHeaders().get("x-actor-retry-started-at")).longValue();
        assertTrue(startedAt > 0 && startedAt <= System.currentTimeMillis());
        assertSame(body, publish.getArgument(4));
        verify(publishChannelOf(WAITING_QUEUE, QUEUE)).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);

        deliverFailingMessage(sideline, 12L);
        verify(sideline.channel()).basicAck(12L, false);
        verify(publishChannelOf(SIDELINE_WAITING_QUEUE, SIDELINE_QUEUE)).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);

        actor.stop();
    }

    @Test
    void sidelineRetryBudgetIsIndependentOfMainConsumer() throws Exception {
        var sidelineConfig = sidelineConfig();
        sidelineConfig.setRetryConfig(asyncRetry(1));
        var actor = startActor(actorConfig(asyncRetry(3), false, null, sidelineConfig));
        try {
            var main = consumerOn(QUEUE);
            var sideline = consumerOn(SIDELINE_QUEUE);
            deliverFailingMessage(main, 61L);
            deliverFailingMessage(sideline, 62L);

            verify(main.channel()).basicAck(61L, false);
            verify(sideline.channel()).basicReject(62L, false);
            assertEquals(QUEUE, single(withFirstArg(callsNamed("basicPublish"), WAITING_QUEUE)).getArgument(1));
            assertTrue(withFirstArg(callsNamed("queueDeclare"), SIDELINE_WAITING_QUEUE).isEmpty());
        } finally {
            actor.stop();
        }
    }

    @Test
    void maxAttemptsOfOneRejectsOnFirstFailureWithoutSchedulingAnyRetryQueue() throws Exception {
        startActor(actorConfig(asyncRetry(1), false, null, null));

        var main = consumerOn(QUEUE);
        deliverFailingMessage(main, 21L);

        verify(main.channel()).basicReject(21L, false);
        verify(main.channel(), never()).basicAck(anyLong(), anyBoolean());
        assertTrue(callsNamed("confirmSelect").isEmpty());
        assertTrue(withFirstArg(callsNamed("queueDeclare"), WAITING_QUEUE).isEmpty());
        assertTrue(withFirstArg(callsNamed("exchangeDeclare"), WAITING_QUEUE).isEmpty());
        assertTrue(withFirstArg(callsNamed("basicPublish"), WAITING_QUEUE).isEmpty());
    }

    @Test
    void retryTopologyIsDeclaredWhenTheActorStartsBeforeAnyFailure() throws Exception {
        var actor = startActor(actorConfig(asyncRetry(3), false, null, null));

        assertEquals("fanout", single(withFirstArg(callsNamed("exchangeDeclare"), WAITING_QUEUE)).getArgument(1));
        var waitingArgs = declaredArguments(single(withFirstArg(callsNamed("queueDeclare"), WAITING_QUEUE)));
        assertEquals(WAIT_MS, ((Number) waitingArgs.get("x-message-ttl")).longValue());
        assertTrue(withFirstArg(callsNamed("basicPublish"), WAITING_QUEUE).isEmpty());
        assertTrue(callsNamed("confirmSelect").isEmpty());

        actor.stop();
    }

    @Test
    void shardedActorsRetryThroughOneWaitingQueueBackToTheFailingShard() throws Exception {
        var actor = startActor(actorConfig(asyncRetry(2), false, 2, null));

        var shardZero = consumerOn(QUEUE_0);
        var shardOne = consumerOn(QUEUE_1);
        deliverFailingMessage(shardOne, 31L);

        verify(shardOne.channel()).basicAck(31L, false);
        verify(shardZero.channel(), never()).basicAck(anyLong(), anyBoolean());
        assertEquals("", single(withFirstArg(callsNamed("queueBind"), WAITING_QUEUE)).getArgument(2));
        assertEquals(QUEUE_1, single(withFirstArg(callsNamed("basicPublish"), WAITING_QUEUE)).getArgument(1));

        actor.stop();
    }

    @Test
    void consumerStopsAfterFailedRetryHandoff() throws Exception {
        var actor = startActor(actorConfig(asyncRetry(3), false, null, null));
        var main = consumerOn(QUEUE);
        deliverFailingMessage(main, 51L);
        var publisher = publishChannelOf(WAITING_QUEUE, QUEUE);
        doThrow(new IOException("broker unavailable"))
                .when(publisher).waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
        try {
            org.junit.jupiter.api.Assertions.assertThrows(
                    io.appform.dropwizard.actors.retry.async.RetrySchedulingException.class,
                    () -> deliverFailingMessage(main, 52L));
            assertTimeoutPreemptively(java.time.Duration.ofMillis(500), actor::stop);

            verify(main.channel(), never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
            verify(main.channel()).basicCancel(main.tag());
            verify(main.channel()).close();
            verify(publisher, times(2)).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());
        } finally {
            actor.stop();
        }
    }

    @Test
    void stopReleasesConsumerPublisherAndSchedulerChannelsAndIgnoresApplicationShutdown() throws Exception {
        var actor = startActor(actorConfig(asyncRetry(3), false, null, sidelineConfig()));

        var main = consumerOn(QUEUE);
        var sideline = consumerOn(SIDELINE_QUEUE);
        deliverFailingMessage(main, 41L);
        var schedulerChannel = publishChannelOf(WAITING_QUEUE, QUEUE);
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
