package io.appform.dropwizard.actors.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.base.helper.MessageBodyHelper;
import io.appform.dropwizard.actors.base.helper.PropertiesHelper;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.compression.CompressionProvider;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;

import java.io.IOException;

@Slf4j
public class UnmanagedPublisher<Message>{

    private final String name;
    private final ActorConfig config;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final String queueName;

    private Channel publishChannel;
    private final PropertiesHelper propertiesHelper;
    private final MessageBodyHelper messageBodyHelper;

    private final boolean compressionEnabled;
    private final boolean ttlDelayEnabled;

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

        this.propertiesHelper = new PropertiesHelper(config);
        this.messageBodyHelper = new MessageBodyHelper();

        this.compressionEnabled = config.getCompressionConfig() != null &&
                config.getCompressionConfig().isEnableCompression() &&
                config.getCompressionConfig().getCompressionAlgorithm() != null;

        this.ttlDelayEnabled = config.getDelayType() == DelayType.TTL;
    }

    public final void publishWithDelay(Message message, long delayMilliseconds) throws Exception {
        log.info("Publishing message to exchange with delay: {}", delayMilliseconds);
        if (!config.isDelayed()) {
            log.warn("Publishing delayed message to non-delayed queue queue:{}", queueName);
        }

        val properties = propertiesHelper.createPropertiesWithDelay(delayMilliseconds, compressionEnabled,
                ttlDelayEnabled);
        val exchange = ttlDelayEnabled ? ttlExchange(config) : config.getExchange();
        val messageBody = createMessageBody(message, properties);

        publishChannel.basicPublish(exchange, queueName, properties, messageBody);
    }

    public final void publish(Message message) throws Exception {
        publish(message, MessageProperties.MINIMAL_PERSISTENT_BASIC);
    }

    public final void publish(Message message, AMQP.BasicProperties properties) throws Exception {
        val messageBody = createMessageBody(message, propertiesHelper.addMessageHeaders(properties, compressionEnabled));
        publishChannel.basicPublish(config.getExchange(), queueName, properties, messageBody);
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
        connection.ensure(queueName + "_SIDELINE", queueName, dlx,
                connection.rmqOpts(config));
        connection.ensure(queueName,
                config.getExchange(),
                connection.rmqOpts(dlx, config));
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
            log.info("Publisher channel {} closed.", name);
        } catch (Exception e) {
            log.error(String.format("Error closing publisher:%s", name), e);
            throw e;
        }
    }

    protected final RMQConnection connection() {
        return connection;
    }

    protected final ObjectMapper mapper() {
        return mapper;
    }

    private byte[] createMessageBody(final Message message,
                                     final AMQP.BasicProperties properties) throws Exception {

        var byteMessage = mapper().writeValueAsBytes(message);
        return messageBodyHelper.compressMessage(byteMessage, properties);
    }
}
