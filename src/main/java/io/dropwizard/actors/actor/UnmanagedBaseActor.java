package io.dropwizard.actors.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.rabbitmq.client.*;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.retry.RetryStrategy;
import io.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * This actor can be derived to directly call start/stop.
 * This is not Managed and will not be automatically started by dropwizard.
 */
@Data
@EqualsAndHashCode
@ToString
@Slf4j
public class UnmanagedBaseActor<Message> {

    private final String name;
    private final ActorConfig config;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final Class<? extends Message> clazz;
    private final int prefetchCount;
    private final MessageHandlingFunction<Message, Boolean> handlerFunction;
    private final Function<Throwable, Boolean> errorCheckFunction;
    private final String queueName;
    private final RetryStrategy retryStrategy;

    private Channel publishChannel;
    private List<Handler> handlers = Lists.newArrayList();

    public UnmanagedBaseActor(
            String name,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            Class<? extends Message> clazz,
            MessageHandlingFunction<Message, Boolean> handlerFunction,
            Function<Throwable, Boolean> errorCheckFunction ) {
        this.name = name;
        this.config = config;
        this.connection = connection;
        this.mapper = mapper;
        this.clazz = clazz;
        this.prefetchCount = config.getPrefetchCount();
        this.handlerFunction = handlerFunction;
        this.errorCheckFunction = errorCheckFunction;
        this.queueName  = String.format("%s.%s", config.getPrefix(), name);
        this.retryStrategy = retryStrategyFactory.create(config.getRetryConfig());
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

    private boolean handle(Message message) throws Exception {
        return handlerFunction.apply(message);
    }

    private class Handler extends DefaultConsumer {
        private final ObjectMapper mapper;
        private final Class<? extends Message> clazz;

        @Getter
        @Setter
        private String tag;

        private Handler(Channel channel,
                        ObjectMapper mapper,
                        Class<? extends Message> clazz,
                        int prefetchCount) throws Exception {
            super(channel);
            this.mapper = mapper;
            this.clazz = clazz;
            getChannel().basicQos(prefetchCount);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                                   AMQP.BasicProperties properties, byte[] body) throws IOException {
            try {
                final Message message = mapper.readValue(body, clazz);

                boolean success = retryStrategy.execute(() -> handle(message));
                if(success) {
                    getChannel().basicAck(envelope.getDeliveryTag(), false);
                }
                else {
                    getChannel().basicReject(envelope.getDeliveryTag(), false);
                }
            } catch (Throwable t) {
                log.error("Error processing message...", t);
                if (errorCheckFunction.apply(t)) {
                    log.warn("Acked message due to exception: ", t);
                    getChannel().basicAck(envelope.getDeliveryTag(), false);
                }
                else {
                    getChannel().basicReject(envelope.getDeliveryTag(), false);
                }
            }
        }

    }

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
            final Handler handler = new Handler(consumeChannel, mapper, clazz, prefetchCount);
            final String tag = consumeChannel.basicConsume(queueName, false, handler);
            handler.setTag(tag);
            handlers.add(handler);
            log.info("Started consumer {} of type {}", i, name);
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

    public void stop() throws Exception {
        try {
            publishChannel.close();
        } catch (Exception e) {
            log.error(String.format("Error closing publisher:%s" , name), e);
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
