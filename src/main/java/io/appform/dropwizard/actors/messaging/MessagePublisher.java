package io.appform.dropwizard.actors.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Return;
import com.rabbitmq.client.ShutdownSignalException;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.messaging.MessagingNames.ExchangeNames;
import io.appform.dropwizard.actors.messaging.config.ExchangeSpec;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes messages to an exchange. Which queues receive a message is decided by the exchange type and the queues'
 * bindings, never by the publisher.
 *
 * <p>Delivery guarantees (non-delayed exchanges):
 * <ul>
 *   <li>every publish uses {@code mandatory=true}; if no binding matches, the broker returns the message;</li>
 *   <li>with {@code publisherConfirms} (default) the publish blocks until the broker confirms it, and a returned
 *       (unroutable) message fails the publish with {@link UnroutableMessageException};</li>
 *   <li>without confirms, returned messages are only logged (the publish has already returned).</li>
 * </ul>
 * The delayed-message plugin does not support {@code mandatory}, so on a delayed exchange an unroutable message is
 * not detected: it is dropped silently.
 *
 * <p>Thread-safe: publishes are serialized on one channel. If the channel gets closed (e.g. a confirm timed out or the
 * broker rejected an operation), it is reopened on the next publish.
 */
@Slf4j
public class MessagePublisher<M> {

    /**
     * AMQP limits a routing key (short string) to 255 bytes.
     */
    static final int MAX_ROUTING_KEY_BYTES = 255;

    private final ExchangeNames names;
    private final ExchangeSpec spec;
    private final RoutingKeyResolver<M> routingKeyResolver;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final RMQObserver observer;
    private final Object lock = new Object();

    private Channel channel;
    private boolean started;
    private volatile Return lastReturn;

    MessagePublisher(final ExchangeNames names,
                   final ExchangeSpec spec,
                   final RoutingKeyResolver<M> routingKeyResolver,
                   final RMQConnection connection,
                   final ObjectMapper mapper) {
        Preconditions.checkArgument(!spec.getType().usesRoutingKey() || routingKeyResolver != null,
                "A RoutingKeyResolver is required to publish to %s exchange '%s'", spec.getType(), names.logical());
        Preconditions.checkArgument(spec.getType().usesRoutingKey() || routingKeyResolver == null,
                "FANOUT exchange '%s' ignores routing keys; do not pass a RoutingKeyResolver", names.logical());
        this.names = names;
        this.spec = spec;
        this.routingKeyResolver = routingKeyResolver;
        this.connection = connection;
        this.mapper = mapper;
        this.observer = connection.getRootObserver();
    }

    /**
     * Logical exchange name (as used in code).
     */
    public String getExchange() {
        return names.logical();
    }

    void start() throws IOException {
        synchronized (lock) {
            openChannel();
            started = true;
        }
    }

    void stop() throws IOException, TimeoutException {
        synchronized (lock) {
            started = false;
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
    }

    /**
     * Publishes {@code message} to the exchange. The routing key comes from the {@link RoutingKeyResolver} (empty for
     * FANOUT).
     *
     * @throws UnroutableMessageException if no binding matched (publisher confirms on)
     * @throws IllegalArgumentException   if the routing key is blank or longer than 255 bytes
     * @throws IllegalStateException      if the publisher is not started
     * @throws io.appform.dropwizard.actors.common.RabbitmqActorException if serialization or publishing fails, or the
     *                                    confirm times out
     */
    public void publish(final M message) {
        publish(message, Map.of());
    }

    /**
     * Same as {@link #publish(Object)}, with extra message headers (e.g. a feature-environment header). Headers set by
     * the library ({@code x-published-at}, {@code x-delay}, {@code x-expire-at}) take precedence. {@code null} means no
     * headers.
     */
    public void publish(final M message, final Map<String, Object> headers) {
        send(message, headers, null, null);
    }

    /**
     * Publishes with a delay. Requires the exchange to be configured with {@code delayed: true}. Otherwise behaves like
     * {@link #publish(Object)}, except that unroutable messages are not detected (see the class javadoc).
     */
    public void publishWithDelay(final M message, final Duration delay) {
        publishWithDelay(message, delay, Map.of());
    }

    /**
     * Same as {@link #publishWithDelay(Object, Duration)}, with extra message headers ({@code null} means none).
     */
    public void publishWithDelay(final M message, final Duration delay, final Map<String, Object> headers) {
        Preconditions.checkState(spec.isDelayed(),
                "Exchange '%s' is not delayed; set delayed: true to publish with a delay", names.logical());
        Preconditions.checkArgument(delay != null && !delay.isNegative(), "delay must not be negative");
        send(message, headers, delay, null);
    }

    /**
     * Publishes a message that consumers treat as expired after {@code expiry}: it is handed to
     * {@code QueueHandler.handleExpired} instead of {@code handle}. Otherwise behaves like {@link #publish(Object)}.
     */
    public void publishWithExpiry(final M message, final Duration expiry) {
        publishWithExpiry(message, expiry, Map.of());
    }

    /**
     * Same as {@link #publishWithExpiry(Object, Duration)}, with extra message headers ({@code null} means none).
     */
    public void publishWithExpiry(final M message, final Duration expiry, final Map<String, Object> headers) {
        Preconditions.checkArgument(expiry != null && !expiry.isNegative() && !expiry.isZero(),
                "expiry must be positive");
        send(message, headers, null, expiry);
    }

    private void send(final M message, final Map<String, Object> headers, final Duration delay, final Duration expiry) {
        Objects.requireNonNull(message, "message");
        final String routingKey = routingKey(message);
        final PublishObserverContext context = PublishObserverContext.builder()
                .queueName(names.broker())
                .messageProperties(properties(headers, delay, expiry))
                .build();
        // Observers (metrics, tracing...) may replace the properties. The headers the library relies on are added
        // after them, like UnmanagedPublisher does, so an observer can't drop them.
        observer.executePublish(context, details -> {
            doSend(message, routingKey, enrich(details.getMessageProperties()));
            return null;
        });
    }

    private void doSend(final M message, final String routingKey, final AMQP.BasicProperties properties) {
        // The delayed-message plugin does not support mandatory: it would return every message.
        final boolean mandatory = !spec.isDelayed();
        try {
            // Serialize outside the lock, so a slow or failing serialization doesn't hold up other publishes.
            final byte[] body = mapper.writeValueAsBytes(message);

            // The lock is held until the confirm arrives: one channel, one publish in flight at a time. That keeps the
            // returned-message check below unambiguous, at the cost of serializing publishes on this exchange.
            // Without confirms it is still needed (and held only for basicPublish): it guards reopening the channel
            // and racing with stop(), so no channel is leaked or used while being closed.
            synchronized (lock) {
                Preconditions.checkState(started, "Publisher for exchange '%s' is not started", names.logical());

                // A confirm timeout or a channel-level broker error closes the channel for good; reopen it rather
                // than failing every publish until restart. A connection failure is different: automatic recovery
                // restores this same channel, so fail fast until it does (see ChannelRecovery).
                if (channel == null || !channel.isOpen()) {
                    ChannelRecovery.failIfAwaitingConnectionRecovery(channel, "exchange '" + names.logical() + "'");
                    log.warn("Publish channel for exchange {} is closed ({}); reopening", names.logical(),
                            ChannelRecovery.describeCloseReason(channel));
                    openChannel();
                }

                lastReturn = null;
                channel.basicPublish(names.broker(), routingKey, mandatory, properties, body);

                if (spec.isPublisherConfirms()) {
                    // The broker sends basic.return for an unroutable mandatory message before its confirm, so once
                    // the confirm has arrived, onReturn has already run for this message if it was unroutable.
                    channel.waitForConfirmsOrDie(spec.getConfirmTimeoutMs());
                    final Return returned = lastReturn;
                    if (returned != null
                            && Objects.equals(returned.getProperties().getMessageId(), properties.getMessageId())) {
                        throw new UnroutableMessageException(names.logical(), names.broker(), routingKey,
                                returned.getReplyText());
                    }
                }
            }
        } catch (TimeoutException e) {
            throw RabbitmqActorException.propagate(String.format(
                    "Timed out after %d ms waiting for publisher confirm on exchange '%s'. The message may or may not "
                            + "have reached the broker; retrying can publish it twice.",
                    spec.getConfirmTimeoutMs(), names.logical()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RabbitmqActorException.propagate(String.format(
                    "Interrupted while publishing to exchange '%s'", names.logical()), e);
        } catch (IOException e) {
            throw RabbitmqActorException.propagate(String.format(
                    "Error publishing to exchange '%s'", names.logical()), e);
        } catch (ShutdownSignalException e) {
            // Unchecked: the broker closed the channel or connection (e.g. 404 for a missing exchange). The channel is
            // reopened on the next publish.
            throw RabbitmqActorException.propagate(String.format(
                    "Channel closed while publishing to exchange '%s': %s", names.logical(), e.getMessage()), e);
        }
    }

    /**
     * Must be called while holding {@link #lock}.
     */
    private void openChannel() throws IOException {
        discardChannel();
        channel = connection.newChannel();
        if (spec.isPublisherConfirms()) {
            channel.confirmSelect();
        }
        channel.addReturnListener(this::onReturn);
    }

    /**
     * Must be called while holding {@link #lock}. Aborts the current channel before it is replaced. With automatic
     * connection recovery, a channel closed by a connection failure would otherwise be restored by the client once the
     * connection recovers, and stay open on the connection with nothing using it.
     */
    private void discardChannel() {
        if (channel == null) {
            return;
        }
        try {
            channel.abort();
        } catch (Exception e) {
            log.debug("Error aborting replaced channel", e);
        }
        channel = null;
    }

    private void onReturn(final Return returned) {
        lastReturn = returned;
        // WARN, not ERROR: with publisher confirms the publish also fails with UnroutableMessageException, so the
        // caller decides how serious it is; without confirms this log line is the only signal.
        log.warn("Unroutable message returned by broker: exchange={} routingKey={} replyCode={} replyText={} "
                        + "messageId={}", returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(),
                returned.getReplyText(), returned.getProperties().getMessageId());
    }

    private String routingKey(final M message) {
        if (!spec.getType().usesRoutingKey()) {
            return "";
        }
        final String key = routingKeyResolver.resolve(message);
        Preconditions.checkArgument(key != null && !key.isBlank(),
                "RoutingKeyResolver returned a blank routing key for exchange '%s'", names.logical());
        Preconditions.checkArgument(key.getBytes(StandardCharsets.UTF_8).length <= MAX_ROUTING_KEY_BYTES,
                "Routing key for exchange '%s' exceeds %s bytes: %s", names.logical(), MAX_ROUTING_KEY_BYTES, key);
        return key;
    }

    /**
     * Properties describing the message, handed to the observers: persistent delivery, the caller's headers, and the
     * delay/expiry headers.
     */
    private static AMQP.BasicProperties properties(final Map<String, Object> headers,
                                                   final Duration delay,
                                                   final Duration expiry) {
        final Map<String, Object> allHeaders = headers == null ? new HashMap<>() : new HashMap<>(headers);
        if (delay != null) {
            allHeaders.put("x-delay", delay.toMillis());
        }
        if (expiry != null) {
            allHeaders.put(Constants.MESSAGE_EXPIRY_TEXT, Instant.now().toEpochMilli() + expiry.toMillis());
        }
        return new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .headers(allHeaders)
                .build();
    }

    /**
     * Adds what the library itself relies on to the properties coming out of the observers: {@code x-published-at}
     * (consumers' delay metrics) and a fresh {@code messageId} (matching a returned message to this publish).
     */
    private static AMQP.BasicProperties enrich(final AMQP.BasicProperties properties) {
        final Map<String, Object> headers = new HashMap<>();
        if (properties.getHeaders() != null) {
            headers.putAll(properties.getHeaders());
        }
        headers.put(Constants.MESSAGE_PUBLISHED_TEXT, Instant.now().toEpochMilli());
        return properties.builder()
                .messageId(UUID.randomUUID().toString())
                .headers(headers)
                .build();
    }
}
