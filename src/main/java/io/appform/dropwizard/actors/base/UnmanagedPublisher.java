package io.appform.dropwizard.actors.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.config.TracingConfiguration;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.retry.config.ConfigProvider;
import io.appform.dropwizard.actors.tracing.HeadersMapExtractAdapter;
import io.appform.dropwizard.actors.tracing.HeadersMapInjectAdapter;
import io.appform.dropwizard.actors.tracing.SpanDecorator;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import io.appform.dropwizard.actors.tracing.TracingHandler;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.RandomUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class UnmanagedPublisher<Message> {

    private final String name;
    private final ActorConfig config;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final String queueName;
    private final ConfigProvider configProvider;
    private Channel publishChannel;
    /**
     * ToDo: remove these 3 parameters after testing
     */
    private int constructorCount = 0;
    private int publishCount = 0;
    private int publishWithDelayCount = 0;

    public UnmanagedPublisher(
            String name,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper,
            ConfigProvider configProvider) {
        this.name = NamingUtils.prefixWithNamespace(name);
        this.config = config;
        this.connection = connection;
        this.mapper = mapper;
        this.queueName = NamingUtils.queueName(config.getPrefix(), name);
        this.configProvider = configProvider;
        this.constructorCount += 1;
    }

    public final void publishWithDelay(Message message, long delayMilliseconds) throws Exception {
        this.publishWithDelayCount += 1;
        log.info("Publishing message to exchange with delay: {}", delayMilliseconds);
        if (!config.isDelayed()) {
            log.warn("Publishing delayed message to non-delayed queue queue:{}", queueName);
        }

        log.info("constructorCount is : {} and publishWithDelayCount is : {}", constructorCount, publishWithDelayCount);
        if (config.getDelayType() == DelayType.TTL) {
            var properties = new AMQP.BasicProperties.Builder()
                    .expiration(String.valueOf(delayMilliseconds))
                    .deliveryMode(2)
                    .build();
            if (!configProvider.getTracingConfiguration().isTracingEnabled()) {
                log.info("tracing is disabled hence adding returning early");
                publishChannel.basicPublish(ttlExchange(config),
                        queueName,
                        properties,
                        mapper().writeValueAsBytes(message));
                return;
            }
            log.info("since tracing is enabled hence adding traces of rmq");
            val tracer = TracingHandler.getTracer();
            val span = TracingHandler.buildSpan(ttlExchange(config), queueName, properties, tracer);
            val scope = TracingHandler.activateSpan(tracer, span);
            properties = TracingHandler.inject(properties, span, tracer);
            try {
                publishChannel.basicPublish(ttlExchange(config),
                        queueName,
                        properties,
                        mapper().writeValueAsBytes(message));
            } finally {
                TracingHandler.closeScopeAndSpan(span, scope);
            }
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

    public final void publish(Message message, AMQP.BasicProperties props) throws Exception {
        this.publishCount += 1;
        String routingKey;
        if (config.isSharded()) {
            routingKey = NamingUtils.getShardedQueueName(queueName, getShardId());
        } else {
            routingKey = queueName;
        }

        log.info("constructorCount is : {} and publishCount is : {}", constructorCount, publishCount);
        if (!configProvider.getTracingConfiguration().isTracingEnabled()) {
            log.info("tracing is disabled hence adding returning early");
            publishChannel.basicPublish(config.getExchange(), routingKey, props, mapper().writeValueAsBytes(message));
            return;
        }

        log.info("since tracing is enabled hence adding traces of rmq");
        val tracer = TracingHandler.getTracer();
        val span = TracingHandler.buildSpan(config.getExchange(), routingKey, props, tracer);
        val scope = TracingHandler.activateSpan(tracer, span);
        props = TracingHandler.inject(props, span, tracer);
        try {
            publishChannel.basicPublish(config.getExchange(), routingKey, props, mapper().writeValueAsBytes(message));
        } finally {
            TracingHandler.closeScopeAndSpan(span, scope);
        }
    }

    private final int getShardId() {
        return RandomUtils.nextInt(0, config.getShardCount());
    }

    public final long pendingMessagesCount() {
        try {
            if (config.isSharded()) {
                long messageCount = 0;
                for (int i = 0; i < config.getShardCount(); i++) {
                    String shardedQueueName = NamingUtils.getShardedQueueName(queueName, i);
                    messageCount += publishChannel.messageCount(shardedQueueName);
                }
                return messageCount;
            } else {
                return publishChannel.messageCount(queueName);
            }
        } catch (IOException e) {
            log.error("Issue getting message count. Will return max", e);
        }
        return Long.MAX_VALUE;
    }

    public final long pendingSidelineMessagesCount() {
        try {
            return publishChannel.messageCount(queueName + "_SIDELINE");
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
        connection.ensure(queueName + "_SIDELINE", queueName, dlx,
                connection.rmqOpts(config));
        if (config.isSharded()) {
            int bound = config.getShardCount();
            for (int shardId = 0; shardId < bound; shardId++) {
                connection.ensure(NamingUtils.getShardedQueueName(queueName, shardId), config.getExchange(),
                        connection.rmqOpts(dlx, config));
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
            log.info("Publisher channel closed for [{}] with prefix [{}]", name, config.getPrefix());
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

}
