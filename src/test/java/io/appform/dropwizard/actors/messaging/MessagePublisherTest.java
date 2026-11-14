package io.appform.dropwizard.actors.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Return;
import com.rabbitmq.client.ReturnCallback;
import com.rabbitmq.client.ShutdownSignalException;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.messaging.MessagingNames.ExchangeNames;
import io.appform.dropwizard.actors.messaging.config.ExchangeSpec;
import io.appform.dropwizard.actors.observers.TerminalRMQObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MessagePublisherTest {

    private static final ExchangeNames NAMES = new ExchangeNames("ex", "ns.ex", "ex_SIDELINE");

    private Channel channel;
    private RMQConnection connection;
    private final AtomicReference<ReturnCallback> returnCallback = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        channel = newChannel();
        connection = mock(RMQConnection.class);
        when(connection.getRootObserver()).thenReturn(new TerminalRMQObserver());
        when(connection.newChannel()).thenReturn(channel);
    }

    private Channel newChannel() {
        final Channel c = mock(Channel.class);
        when(c.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            returnCallback.set(inv.getArgument(0));
            return null;
        }).when(c).addReturnListener(any(ReturnCallback.class));
        return c;
    }

    private static ExchangeSpec spec(final ExchangeType type, final boolean confirms, final boolean delayed) {
        return ExchangeSpec.builder().type(type).publisherConfirms(confirms).delayed(delayed).build();
    }

    private MessagePublisher<String> started(final ExchangeSpec spec, final RoutingKeyResolver<String> resolver)
            throws IOException {
        final MessagePublisher<String> publisher =
                new MessagePublisher<>(NAMES, spec, resolver, connection, new ObjectMapper());
        publisher.start();
        return publisher;
    }

    private AMQP.BasicProperties publishedProperties(final boolean mandatory) throws IOException {
        final ArgumentCaptor<AMQP.BasicProperties> props = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(channel).basicPublish(eq("ns.ex"), anyString(), eq(mandatory), props.capture(), any());
        return props.getValue();
    }

    @Test
    void resolverRequiredForRoutingExchangesAndRejectedForFanout() {
        final ObjectMapper mapper = new ObjectMapper();
        final ExchangeSpec direct = spec(ExchangeType.DIRECT, true, false);
        final ExchangeSpec fanout = spec(ExchangeType.FANOUT, true, false);
        assertThrows(IllegalArgumentException.class, () ->
                new MessagePublisher<String>(NAMES, direct, null, connection, mapper));
        assertThrows(IllegalArgumentException.class, () ->
                new MessagePublisher<String>(NAMES, fanout, m -> m, connection, mapper));
    }

    @Test
    void publishesToBrokerExchangeWithResolvedKeyAndWaitsForConfirm() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.TOPIC, true, false), m -> "k." + m);
        verify(channel).confirmSelect();

        publisher.publish("a", Map.of("h", "v"));

        verify(channel).basicPublish(eq("ns.ex"), eq("k.a"), eq(true), any(), eq("\"a\"".getBytes()));
        verify(channel).waitForConfirmsOrDie(5_000);
        final AMQP.BasicProperties props = publishedProperties(true);
        assertEquals(2, props.getDeliveryMode());
        assertTrue(props.getMessageId() != null && !props.getMessageId().isEmpty());
        assertEquals("v", props.getHeaders().get("h"));
        assertTrue(props.getHeaders().containsKey(Constants.MESSAGE_PUBLISHED_TEXT));
    }

    @Test
    void fanoutUsesEmptyRoutingKey() throws Exception {
        started(spec(ExchangeType.FANOUT, true, false), null).publish("a");
        verify(channel).basicPublish(eq("ns.ex"), eq(""), eq(true), any(), any());
    }

    @Test
    void nullHeadersMeanNoHeaders() throws Exception {
        started(spec(ExchangeType.FANOUT, true, false), null).publish("a", null);
        assertTrue(publishedProperties(true).getHeaders().containsKey(Constants.MESSAGE_PUBLISHED_TEXT));
    }

    @Test
    void withoutConfirmsDoesNotWait() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.FANOUT, false, false), null);
        publisher.publish("a");
        verify(channel, never()).confirmSelect();
        verify(channel, never()).waitForConfirmsOrDie(anyLong());
    }

    @Test
    void rejectsBlankOrOversizedRoutingKeys() throws Exception {
        final MessagePublisher<String> blankKey = started(spec(ExchangeType.DIRECT, true, false), m -> " ");
        final MessagePublisher<String> nullKey = started(spec(ExchangeType.DIRECT, true, false), m -> null);
        final MessagePublisher<String> longKey = started(spec(ExchangeType.DIRECT, true, false), m -> "x".repeat(256));
        assertThrows(IllegalArgumentException.class, () -> blankKey.publish("a"));
        assertThrows(IllegalArgumentException.class, () -> nullKey.publish("a"));
        assertThrows(IllegalArgumentException.class, () -> longKey.publish("a"));
        started(spec(ExchangeType.DIRECT, true, false), m -> "x".repeat(255)).publish("a");
        verify(channel, times(1)).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());
    }

    @Test
    void rejectsNullMessageAndPublishBeforeStart() {
        final MessagePublisher<String> publisher = new MessagePublisher<>(NAMES, spec(ExchangeType.FANOUT, true,
                false), null, connection, new ObjectMapper());
        assertThrows(NullPointerException.class, () -> publisher.publish(null));
        assertThrows(IllegalStateException.class, () -> publisher.publish("a"));
    }

    @Test
    void rejectsPublishAfterStop() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.FANOUT, true, false), null);
        publisher.stop();
        verify(channel).close();
        assertThrows(IllegalStateException.class, () -> publisher.publish("a"));
    }

    @Test
    void unroutableMessageFailsPublishWithConfirms() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.DIRECT, true, false), m -> "k");
        doAnswer(inv -> {
            final AMQP.BasicProperties props = inv.getArgument(3);
            returnCallback.get().handle(new Return(312, "NO_ROUTE", "ns.ex", "k", props, new byte[0]));
            return null;
        }).when(channel).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());

        final UnroutableMessageException e =
                assertThrows(UnroutableMessageException.class, () -> publisher.publish("a"));
        assertTrue(e.getMessage().contains("NO_ROUTE"), e.getMessage());
    }

    @Test
    void unroutableMessageDoesNotFailPublishWithoutConfirms() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.DIRECT, false, false), m -> "k");
        doAnswer(inv -> {
            final AMQP.BasicProperties props = inv.getArgument(3);
            returnCallback.get().handle(new Return(312, "NO_ROUTE", "ns.ex", "k", props, new byte[0]));
            return null;
        }).when(channel).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());

        publisher.publish("a");  // only logged: the caller is not told

        verify(channel).basicPublish(eq("ns.ex"), eq("k"), eq(true), any(), any());
        verify(channel, never()).waitForConfirmsOrDie(anyLong());
    }

    @Test
    void staleReturnForAnotherMessageIsIgnored() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.DIRECT, true, false), m -> "k");
        doAnswer(inv -> {
            final AMQP.BasicProperties other = new AMQP.BasicProperties.Builder().messageId("other").build();
            returnCallback.get().handle(new Return(312, "NO_ROUTE", "ns.ex", "k", other, new byte[0]));
            return null;
        }).when(channel).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());

        publisher.publish("a");
    }

    @Test
    void confirmTimeoutAndNackSurfaceAsActorExceptions() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.FANOUT, true, false), null);
        doThrow(new TimeoutException("t")).when(channel).waitForConfirmsOrDie(anyLong());
        final RabbitmqActorException timeout = assertThrows(RabbitmqActorException.class, () -> publisher.publish("a"));
        assertTrue(timeout.getMessage().contains("Timed out"), timeout.getMessage());

        doThrow(new IOException("nack")).when(channel).waitForConfirmsOrDie(anyLong());
        assertThrows(RabbitmqActorException.class, () -> publisher.publish("a"));
    }

    @Test
    void channelLevelCloseIsReopenedOnNextPublish() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.FANOUT, true, false), null);
        final ShutdownSignalException softClose = new ShutdownSignalException(false, false, null, channel);
        doThrow(softClose).when(channel).basicPublish(anyString(), anyString(), anyBoolean(), any(), any());
        assertThrows(RabbitmqActorException.class, () -> publisher.publish("a"));

        when(channel.isOpen()).thenReturn(false);
        when(channel.getCloseReason()).thenReturn(softClose);
        final Channel replacement = newChannel();
        when(connection.newChannel()).thenReturn(replacement);

        publisher.publish("a");

        verify(channel).abort();
        verify(replacement).confirmSelect();
        verify(replacement).basicPublish(eq("ns.ex"), eq(""), eq(true), any(), any());
    }

    @Test
    void connectionFailureFailsFastWithoutReplacingChannel() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.FANOUT, true, false), null);
        when(channel.isOpen()).thenReturn(false);
        when(channel.getCloseReason()).thenReturn(new ShutdownSignalException(true, false, null, null));

        final RabbitmqActorException e = assertThrows(RabbitmqActorException.class, () -> publisher.publish("a"));
        assertTrue(e.getMessage().contains("waiting for automatic recovery"), e.getMessage());
        verify(connection, times(1)).newChannel();
        verify(channel, never()).abort();
    }

    @Test
    void delayedPublishSetsDelayAndSkipsMandatory() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.FANOUT, true, true), null);
        publisher.publishWithDelay("a", Duration.ofSeconds(2));
        assertEquals(2_000L, publishedProperties(false).getHeaders().get("x-delay"));
    }

    @Test
    void delayedPublishValidation() throws Exception {
        final MessagePublisher<String> notDelayed = started(spec(ExchangeType.FANOUT, true, false), null);
        final Duration oneSecond = Duration.ofSeconds(1);
        final Duration negative = Duration.ofSeconds(-1);
        assertThrows(IllegalStateException.class, () -> notDelayed.publishWithDelay("a", oneSecond));
        final MessagePublisher<String> delayed = started(spec(ExchangeType.FANOUT, true, true), null);
        assertThrows(IllegalArgumentException.class, () -> delayed.publishWithDelay("a", negative));
        assertThrows(IllegalArgumentException.class, () -> delayed.publishWithDelay("a", null));
    }

    @Test
    void expiryPublishSetsExpiryHeader() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.FANOUT, true, false), null);
        assertThrows(IllegalArgumentException.class, () -> publisher.publishWithExpiry("a", Duration.ZERO));
        final long before = System.currentTimeMillis();
        publisher.publishWithExpiry("a", Duration.ofMinutes(1));
        final long expireAt = (Long) publishedProperties(true).getHeaders().get(Constants.MESSAGE_EXPIRY_TEXT);
        assertTrue(expireAt >= before + 60_000, "expireAt=" + expireAt);
    }

    @Test
    void libraryHeadersOverrideCallerHeaders() throws Exception {
        final MessagePublisher<String> publisher = started(spec(ExchangeType.FANOUT, true, false), null);
        publisher.publish("a", Map.of(Constants.MESSAGE_PUBLISHED_TEXT, -1L));
        assertNotEquals(-1L, publishedProperties(true).getHeaders().get(Constants.MESSAGE_PUBLISHED_TEXT));
    }
}
