package io.appform.dropwizard.actors.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;

@Slf4j
public class UnmanagedPublisher<Message> {

    private final String name;
    private final ActorConfig config;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final String queueName;

    private Channel publishChannel;

    public UnmanagedPublisher(
            String name,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper) {
        this.name = name;
        this.config = config;
        this.connection = connection;
        this.mapper = mapper;
        this.queueName = NamingUtils.queueName(config.getPrefix(), name);
    }

    public final void publishWithDelay(Message message, long delayMilliseconds) throws Exception {
        log.info("Publishing message to exchange with delay: {}", delayMilliseconds);
        if (!config.isDelayed()) {
            log.warn("Publishing delayed message to non-delayed queue queue:{}", queueName);
        }

        if (config.getDelayType() == DelayType.TTL) {
            publishChannel.basicPublish(ttlExchange(config),
                    queueName,
                    new AMQP.BasicProperties.Builder()
                            .expiration(String.valueOf(delayMilliseconds))
                            .deliveryMode(2)
                            .build(),
                    mapper().writeValueAsBytes(message));
        } else {
            publish(message, new AMQP.BasicProperties.Builder()
                    .headers(Collections.singletonMap("x-delay", delayMilliseconds))
                    .deliveryMode(2)
                    .build());
        }
    }

    public final void publish(Message message) throws Exception {
        publish(message, MessageProperties.MINIMAL_PERSISTENT_BASIC);
    }

    public final void publish(Message message, AMQP.BasicProperties properties) throws Exception {
        publishChannel.basicPublish(config.getExchange(), queueName, properties, mapper().writeValueAsBytes(message));
    }

    public final long pendingMessagesCount() {
        try {
            return publishChannel.messageCount(queueName);
        } catch (IOException e) {
            log.error("Issue getting message count. Will return max", e);
        }
        return Long.MAX_VALUE;
    }

    public void start() throws Exception {
        final String exchange = config.getExchange();
        final String dlx = config.getExchange() + "_SIDELINE";
        if (config.isDelayed()) {
            ensureDelayedExchange(exchange);
        } else {
            ensureExchange(exchange);
        }
        ensureExchange(dlx);

        this.publishChannel = connection.newChannel();
        connection.ensure(queueName + "_SIDELINE", queueName, dlx);
        connection.ensure(queueName, config.getExchange(), connection.rmqOpts(dlx));
        if (config.getDelayType() == DelayType.TTL) {
            connection.ensure(ttlQueue(queueName), queueName, ttlExchange(config), connection.rmqOpts(exchange));
        }
    }

    private void ensureExchange(String exchange) throws IOException {
        connection.channel().exchangeDeclare(
                exchange,
                "direct",
                true,
                false,
                ImmutableMap.<String, Object>builder()
                        .put("x-ha-policy", "all")
                        .put("ha-mode", "all")
                        .build());
        log.info("Created exchange: {}", exchange);
    }

    private void ensureDelayedExchange(String exchange) throws IOException {
        if (config.getDelayType() == DelayType.TTL) {
            ensureExchange(ttlExchange(config));
        } else {
            connection.channel().exchangeDeclare(
                    exchange,
                    "x-delayed-message",
                    true,
                    false,
                    ImmutableMap.<String, Object>builder()
                            .put("x-ha-policy", "all")
                            .put("ha-mode", "all")
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
            publishChannel.close();
            log.info("Closing publisher channel {}", name);
        } catch (Exception e) {
            log.error(String.format("Error closing publisher:%s", name), e);
            throw e;
        }
    }

    protected final RMQConnection connection() throws Exception {
        return connection;
    }

    protected final ObjectMapper mapper() {
        return mapper;
    }
}
