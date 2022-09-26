package com.phonepe.platform.rabbitmq.actor.test.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.phonepe.platform.rabbitmq.actor.test.actor.ActorConfig;
import com.phonepe.platform.rabbitmq.actor.test.actor.DelayType;
import com.phonepe.platform.rabbitmq.actor.test.base.utils.NamingUtils;
import com.phonepe.platform.rabbitmq.actor.test.connectivity.RMQConnection;
import com.phonepe.platform.rabbitmq.actor.test.tracing.HeadersMapExtractAdapter;
import com.phonepe.platform.rabbitmq.actor.test.tracing.HeadersMapInjectAdapter;
import com.phonepe.platform.rabbitmq.actor.test.tracing.SpanDecorator;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
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

    public final void publish(Message message, AMQP.BasicProperties props) throws Exception {
        String routingKey;
        if (config.isSharded()) {
            routingKey = NamingUtils.getShardedQueueName(queueName, getShardId());
        } else {
            routingKey = queueName;
        }
        val tracer = GlobalTracer.get();
        Span span = buildSpan(config.getExchange(), routingKey, props, tracer);
        try (Scope scope = tracer.scopeManager().activate(span)) {
            AMQP.BasicProperties properties = inject(props, span, tracer);
            publishChannel.basicPublish(config.getExchange(), routingKey, properties, mapper().writeValueAsBytes(message));
        } finally {
            span.finish();
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

    private static Span buildSpan(final String exchange,
                                 final String routingKey,
                                 final AMQP.BasicProperties props,
                                 final Tracer tracer) {
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan("send")
                .ignoreActiveSpan()
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER)
                .withTag("routingKey", routingKey);

        SpanContext spanContext = null;

        if (props != null && props.getHeaders() != null) {
            // just in case if span context was injected manually to props in basicPublish
            spanContext = tracer.extract(Format.Builtin.TEXT_MAP,
                    new HeadersMapExtractAdapter(props.getHeaders()));
        }

        if (spanContext == null) {
            Span parentSpan = tracer.activeSpan();
            if (parentSpan != null) {
                spanContext = parentSpan.context();
            }
        }

        if (spanContext != null) {
            spanBuilder.asChildOf(spanContext);
        }

        Span span = spanBuilder.start();
        SpanDecorator.onRequest(exchange, span);

        return span;
    }

    private static AMQP.BasicProperties inject(AMQP.BasicProperties properties, Span span,
                                              Tracer tracer) {

        // Headers of AMQP.BasicProperties is unmodifiableMap therefore we build new AMQP.BasicProperties
        // with injected span context into headers
        Map<String, Object> headers = new HashMap<>();

        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new HeadersMapInjectAdapter(headers));

        if (properties == null) {
            return new AMQP.BasicProperties().builder().headers(headers).build();
        }

        if (properties.getHeaders() != null) {
            headers.putAll(properties.getHeaders());
        }

        return properties.builder()
                .headers(headers)
                .build();
    }
}
