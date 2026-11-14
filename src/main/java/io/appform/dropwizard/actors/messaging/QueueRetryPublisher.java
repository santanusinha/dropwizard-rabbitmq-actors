package io.appform.dropwizard.actors.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes deferred retries to a queue's private delayed retry exchange, which is bound only to that queue. Retries
 * go through the observer chain like normal publishes. If the channel gets closed (e.g. a confirm timed out), it is
 * reopened on the next retry.
 */
@Slf4j
class QueueRetryPublisher {

    static final String DELAY_HEADER = "x-delay";

    private final String retryExchange;
    private final String brokerQueue;
    private final RMQConnection connection;
    private final RMQObserver observer;
    private final ObjectMapper mapper;
    private final boolean confirms;
    private final long confirmTimeoutMs;
    private final Object lock = new Object();
    private Channel channel;
    private boolean started;

    QueueRetryPublisher(final String brokerQueue,
                        final RMQConnection connection,
                        final ObjectMapper mapper,
                        final boolean confirms,
                        final long confirmTimeoutMs) {
        this.retryExchange = MessagingNames.retryExchange(brokerQueue);
        this.brokerQueue = brokerQueue;
        this.connection = connection;
        this.observer = connection.getRootObserver();
        this.mapper = mapper;
        this.confirms = confirms;
        this.confirmTimeoutMs = confirmTimeoutMs;
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
     * Schedules {@code message} for redelivery to this queue after {@code delay}.
     *
     * <p>Application headers (not starting with {@code x-}) are carried forward, and so is the message's expiry
     * ({@code x-expire-at}): a retried message still expires when the original would have. Other broker/library
     * headers ({@code x-death}, {@code x-delay}, {@code x-published-at}...) are dropped.
     */
    void publish(final Object message, final Map<String, Object> originalHeaders, final Duration delay) {
        Preconditions.checkArgument(delay != null && !delay.isNegative(), "delay must not be negative");
        final Map<String, Object> headers = new HashMap<>();
        Object expireAt = null;
        if (originalHeaders != null) {
            originalHeaders.forEach((key, value) -> {
                if (!key.startsWith("x-")) {
                    headers.put(key, value);
                }
            });
            expireAt = originalHeaders.get(Constants.MESSAGE_EXPIRY_TEXT);
        }
        if (expireAt != null) {
            headers.put(Constants.MESSAGE_EXPIRY_TEXT, expireAt);
        }
        headers.put(DELAY_HEADER, delay.toMillis());
        final AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .headers(headers)
                .build();
        final PublishObserverContext context = PublishObserverContext.builder()
                .queueName(retryExchange)
                .messageProperties(properties)
                .build();
        final Object finalExpireAt = expireAt;
        observer.executePublish(context, details -> {
            doPublish(message, enrich(details.getMessageProperties(), delay, finalExpireAt));
            return null;
        });
    }

    /**
     * Adds, after the observers, what the retry relies on: {@code x-delay}, the carried expiry, {@code x-published-at}
     * and a fresh {@code messageId}.
     */
    private static AMQP.BasicProperties enrich(final AMQP.BasicProperties properties,
                                               final Duration delay,
                                               final Object expireAt) {
        final Map<String, Object> headers = new HashMap<>();
        if (properties.getHeaders() != null) {
            headers.putAll(properties.getHeaders());
        }
        headers.put(DELAY_HEADER, delay.toMillis());
        if (expireAt != null) {
            headers.put(Constants.MESSAGE_EXPIRY_TEXT, expireAt);
        }
        headers.put(Constants.MESSAGE_PUBLISHED_TEXT, Instant.now().toEpochMilli());
        return properties.builder()
                .messageId(UUID.randomUUID().toString())
                .headers(headers)
                .build();
    }

    private void doPublish(final Object message, final AMQP.BasicProperties properties) {
        try {
            final byte[] body = mapper.writeValueAsBytes(message);
            synchronized (lock) {
                Preconditions.checkState(started, "Retry publisher for queue '%s' is not started", brokerQueue);
                if (channel == null || !channel.isOpen()) {
                    ChannelRecovery.failIfAwaitingConnectionRecovery(channel, "queue '" + brokerQueue + "'");
                    log.warn("Retry channel for queue {} is closed ({}); reopening", brokerQueue,
                            ChannelRecovery.describeCloseReason(channel));
                    openChannel();
                }
                channel.basicPublish(retryExchange, brokerQueue, properties, body);
                if (confirms) {
                    channel.waitForConfirmsOrDie(confirmTimeoutMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw RabbitmqActorException.propagate(String.format(
                    "Interrupted while scheduling retry for queue '%s'", brokerQueue), e);
        } catch (IOException | TimeoutException | ShutdownSignalException e) {
            // ShutdownSignalException is unchecked: the broker closed the channel or connection. The channel is
            // reopened on the next retry.
            throw RabbitmqActorException.propagate(String.format(
                    "Error scheduling retry for queue '%s'", brokerQueue), e);
        }
    }

    /**
     * Must be called while holding {@link #lock}.
     */
    private void openChannel() throws IOException {
        discardChannel();
        channel = connection.newChannel();
        if (confirms) {
            channel.confirmSelect();
        }
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
}
