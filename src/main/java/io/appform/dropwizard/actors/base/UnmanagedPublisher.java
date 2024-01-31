package io.appform.dropwizard.actors.base;

import static io.appform.dropwizard.actors.common.Constants.MESSAGE_EXPIRY_TEXT;
import static io.appform.dropwizard.actors.common.Constants.MESSAGE_PUBLISHED_TEXT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.RandomUtils;

@Slf4j
public class UnmanagedPublisher<Message> {

    private final String name;
    private final ActorConfig config;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final String queueName;
    private final RMQObserver observer;
    private Channel publishChannel;

    public UnmanagedPublisher(
            String name,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper) {
        this.name = NamingUtils.prefixWithNamespace(name);
        this.config = config;
        this.connection = connection;
        this.mapper = mapper;
        this.queueName = NamingUtils.queueName(config.getPrefix(), name);
        this.observer = connection.getRootObserver();
    }

    public final void publishWithDelay(final Message message, final long delayMilliseconds) throws Exception {
        log.info("Publishing message to exchange with delay: {}", delayMilliseconds);
        val properties = getPropertiesWithDelay(delayMilliseconds);
        publishWithDelay(message, properties);
    }

    private final void publishWithDelay(final Message message, final AMQP.BasicProperties properties) throws Exception {
        if (!config.isDelayed()) {
            log.warn("Publishing delayed message to non-delayed queue queue:{}", queueName);
        }
        if (config.getDelayType() == DelayType.TTL) {
            val routingKey = getRoutingKey();
            val context = PublishObserverContext.builder()
                    .queueName(queueName)
                    .build();
            observer.executePublish(context, () -> {
                try {
                    publishChannel.basicPublish(ttlExchange(config),
                            routingKey, properties,
                            mapper().writeValueAsBytes(message));
                } catch (IOException e) {
                    log.error("Error while publishing: {}", e);
                    throw RabbitmqActorException.propagate(e);
                }
                return null;
            });
        } else {
            publish(message, properties);
        }
    }
    public final void publishWithExpiry(final Message message, final long expiryInMs) throws Exception {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .build();
        val finalProperties = getPropertiesWithExpiry(properties, expiryInMs);
        publish(message, finalProperties);
    }

    public final void publishWithDelayAndExpiry(final Message message,
                                                final long expiryInMs,
                                                final long delayMilliseconds) throws Exception {
        AMQP.BasicProperties properties = getPropertiesWithDelay(delayMilliseconds);
        val finalProperties = getPropertiesWithExpiry(properties, expiryInMs);
        publishWithDelay(message, finalProperties);

    }

    public final void publish(final Message message) throws Exception {
        publish(message, MessageProperties.MINIMAL_PERSISTENT_BASIC);
    }

    public final void publish(final Message message, final AMQP.BasicProperties properties) throws Exception {
        val routingKey = getRoutingKey();
        val context = PublishObserverContext.builder()
                .queueName(queueName)
                .build();
        observer.executePublish(context, () -> {
            val enrichedProperties = getEnrichedProperties(properties);
            try {
                publishChannel.basicPublish(config.getExchange(), routingKey, enrichedProperties, mapper().writeValueAsBytes(message));
            } catch (IOException e) {
                log.error("Error while publishing: {}", e);
                throw RabbitmqActorException.propagate(e);
            }
            return null;
        });
    }

    private AMQP.BasicProperties getEnrichedProperties(AMQP.BasicProperties properties) {
        HashMap<String, Object> enrichedHeaders = new HashMap<>();
        if (properties.getHeaders() != null) {
            enrichedHeaders.putAll(properties.getHeaders());
        }
        enrichedHeaders.put(MESSAGE_PUBLISHED_TEXT, Instant.now().toEpochMilli());
        return properties.builder()
                .headers(Collections.unmodifiableMap(enrichedHeaders))
                .build();
    }

    private int getShardId() {
        return RandomUtils.nextInt(0, config.getShardCount());
    }

    private AMQP.BasicProperties getPropertiesWithDelay(final long delayMilliseconds){
        if(config.getDelayType() == DelayType.TTL){
            return new AMQP.BasicProperties.Builder()
                    .expiration(String.valueOf(delayMilliseconds))
                    .deliveryMode(2)
                    .build();
        }
            return new AMQP.BasicProperties.Builder()
                    .headers(Collections.singletonMap("x-delay", delayMilliseconds))
                    .deliveryMode(2)
                    .build();
    }

    private AMQP.BasicProperties getPropertiesWithExpiry(final AMQP.BasicProperties properties, final long expiryInMs){
        if (expiryInMs <= 0) {
            return properties;
        }
        val expiresAt = Instant.now().toEpochMilli() + expiryInMs;
        return new AMQP.BasicProperties.Builder()
                .headers(ImmutableMap.of(MESSAGE_EXPIRY_TEXT, expiresAt))
                .build();
    }

    public final long pendingMessagesCount() {
        try {
            if (config.isSharded()) {
                long messageCount  = 0 ;
                for (int i = 0; i < config.getShardCount(); i++) {
                    String shardedQueueName = NamingUtils.getShardedQueueName(queueName, i);
                    messageCount += publishChannel.messageCount(shardedQueueName);
                }
                return messageCount;
            }
            else {
                return publishChannel.messageCount(queueName);
            }
        } catch (IOException e) {
            log.error("Issue getting message count. Will return max", e);
        }
        return Long.MAX_VALUE;
    }

    public final long pendingSidelineMessagesCount() {
        try {
            return publishChannel.messageCount(NamingUtils.getSideline(queueName));
        } catch (IOException e) {
            log.error("Issue getting message count. Will return max", e);
        }
        return Long.MAX_VALUE;
    }

    public void start() throws Exception {
        final String exchange = config.getExchange();
        final String dlx = NamingUtils.getSideline(config.getExchange());
        if (config.isDelayed()) {
            ensureDelayedExchange(exchange);
        } else {
            ensureExchange(exchange);
        }
        ensureExchange(dlx);

        this.publishChannel = connection.newChannel();
        String sidelineQueueName = NamingUtils.getSideline(queueName);
        connection.ensure(sidelineQueueName, queueName, dlx, connection.rmqOpts(config));
        if (config.isSharded()) {
            int bound = config.getShardCount();
            for (int shardId = 0; shardId < bound; shardId++) {
                String shardedQueueName = NamingUtils.getShardedQueueName(queueName, shardId);
                connection.ensure(shardedQueueName, config.getExchange(), connection.rmqOpts(dlx, config));
                connection.addBinding(sidelineQueueName, dlx, shardedQueueName);
            }
        } else {
            connection.ensure(queueName, config.getExchange(), connection.rmqOpts(dlx, config));
        }

        if (config.getDelayType() == DelayType.TTL) {
            connection.ensure(ttlQueue(queueName),
                    queueName,
                    ttlExchange(config),
                    connection.rmqOpts(exchange, config));
        }
    }

    private void ensureExchange(String exchange) throws IOException {
        connection.channel().exchangeDeclare(
                exchange,
                "direct", true);
        log.info("Created exchange: {}", exchange);
    }

    private void ensureDelayedExchange(String exchange) throws IOException {
        if (config.getDelayType() == DelayType.TTL) {
            ensureExchange(ttlExchange(config));
        } else {
            // https://blog.rabbitmq.com/posts/2015/04/scheduling-messages-with-rabbitmq/
            connection.channel().exchangeDeclare(
                    exchange,
                    "x-delayed-message",
                    true,
                    false,
                    ImmutableMap.<String, Object>builder()
                            .put("x-delayed-type", "direct")
                            .build());
            log.info("Created delayed exchange: {}", exchange);
        }
    }

    private String ttlExchange(ActorConfig actorConfig) {
        return String.format("%s_TTL", actorConfig.getExchange());
    }

    private String ttlQueue(String queueName) {
        return String.format("%s_TTL", queueName);
    }

    public void stop() throws Exception {
        try {
            if(publishChannel.isOpen()) {
                publishChannel.close();
                log.info("Publisher channel closed for [{}] with prefix [{}]", name, config.getPrefix());
            } else {
                log.warn("Publisher channel already closed for [{}] with prefix [{}]", name, config.getPrefix());
            }
        } catch (Exception e) {
            log.error(String.format("Error closing publisher channel for [%s] with prefix [%s]", name, config.getPrefix()), e);
            throw e;
        }
    }

    protected final RMQConnection connection() {
        return connection;
    }

    protected final ObjectMapper mapper() {
        return mapper;
    }

    private String getRoutingKey() {
        return config.isSharded() ? NamingUtils.getShardedQueueName(queueName, getShardId()) : queueName;
    }
}
