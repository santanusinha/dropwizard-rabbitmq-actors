package io.dropwizard.actors.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.rabbitmq.client.*;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.retry.RetryStrategy;
import io.dropwizard.actors.retry.RetryStrategyFactory;
import io.dropwizard.lifecycle.Managed;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Actor to be taken
 */
@Data
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class  Actor<MessageType  extends Enum<MessageType>, Message> implements Managed {

    private final MessageType type;
    private final ActorConfig config;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final Class<? extends Message> clazz;
    private final Set<Class<?>> droppedExceptionTypes;
    private final int prefetchCount;
    private final String queueName;
    private final RetryStrategy retryStrategy;

    private Channel publishChannel;
    private List<Handler> handlers = Lists.newArrayList();

    protected Actor(
            MessageType type,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        this.type = type;
        this.config = config;
        this.connection = connection;
        this.mapper = mapper;
        this.clazz = clazz;
        this.droppedExceptionTypes = droppedExceptionTypes;
        this.prefetchCount = config.getPrefetchCount();
        this.queueName  = String.format("%s.%s", config.getPrefix(), type.name());
        this.retryStrategy = retryStrategyFactory.create(config.getRetryConfig());
    }

    abstract protected boolean handle(Message message) throws Exception;


    private class Handler extends DefaultConsumer {
        private final ObjectMapper mapper;
        private final Class<? extends Message> clazz;
        private final Set<Class<?>> droppedExceptionTypes;
        private final Actor<MessageType, Message> actor;

        @Getter
        @Setter
        private String tag;

        private Handler(Channel channel,
                        ObjectMapper mapper,
                        Class<? extends Message> clazz,
                        Set<Class<?>> droppedExceptionTypes,
                        int prefetchCount,
                        Actor<MessageType, Message> actor) throws Exception {
            super(channel);
            this.mapper = mapper;
            this.clazz = clazz;
            this.droppedExceptionTypes = droppedExceptionTypes;
            this.actor = actor;
            getChannel().basicQos(prefetchCount);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                                   AMQP.BasicProperties properties, byte[] body) throws IOException {
            try {
                final Message message = mapper.readValue(body, clazz);

                boolean success = retryStrategy.execute(() -> actor.handle(message));
                if(success) {
                    getChannel().basicAck(envelope.getDeliveryTag(), false);
                }
                else {
                    getChannel().basicReject(envelope.getDeliveryTag(), false);
                }
            } catch (Throwable t) {
                log.error("Error processing message...", t);
                if (droppedExceptionTypes
                        .stream()
                        .anyMatch(exceptionType -> ClassUtils.isAssignable(t.getClass(), exceptionType))) {
                    log.warn("Acked message due to exception: ", t);
                    getChannel().basicAck(envelope.getDeliveryTag(), false);
                }
                else {
                    getChannel().basicReject(envelope.getDeliveryTag(), false);
                }
            }
        }

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
                            .headers(Collections.singletonMap("x-message-ttl", delayMilliseconds))
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

    @Override
    public void start() throws Exception {
        final String exchange = config.getExchange();
        final String dlx = config.getExchange() + "_SIDELINE";
        if(config.isDelayed()) {
            ensureDelayedExchange(exchange);
        }
        else {
            ensureExchange(exchange);
        }
        ensureExchange(dlx);

        this.publishChannel = connection.newChannel();
        connection.ensure(queueName + "_SIDELINE", queueName, dlx);
        connection.ensure(queueName, config.getExchange(), connection.rmqOpts(dlx));
        if (config.getDelayType() == DelayType.TTL) {
            connection.ensure(ttlQueue(queueName), queueName, ttlExchange(config), connection.rmqOpts(exchange));
        }
        for (int i = 1; i <= config.getConcurrency(); i++) {
            Channel consumeChannel = connection.newChannel();
            final Handler handler = new Handler(consumeChannel,
                    mapper, clazz, droppedExceptionTypes, prefetchCount, this);
            final String tag = consumeChannel.basicConsume(queueName, false, handler);
            handler.setTag(tag);
            handlers.add(handler);
            log.info("Started consumer {} of type {}", i, type.name());
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
    }

    private void ensureDelayedExchange(String exchange) throws IOException {
        if (config.getDelayType() == DelayType.TTL){
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
        }
    }

    private String ttlExchange(ActorConfig actorConfig) {
        return String.format("%s_TTL", actorConfig.getExchange());
    }

    private String ttlQueue(String queueName) {
        return String.format("%s_TTL", queueName);
    }

    @Override
    public void stop() throws Exception {
        try {
            publishChannel.close();
        } catch (Exception e) {
            log.error(String.format("Error closing publisher:%s" , type), e);
        }
        handlers.forEach(handler -> {
            try {
                final Channel channel = handler.getChannel();
                channel.basicCancel(handler.getTag());
                channel.close();
            } catch (Exception e) {
                log.error(String.format("Error cancelling consumer: %s", handler.getTag()), e);
            }
        });
    }

    protected final RMQConnection connection() throws Exception {
        return connection;
    }

    protected final ObjectMapper mapper() {
        return mapper;
    }

}
