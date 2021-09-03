package io.appform.dropwizard.actors.base.helper;

import com.google.common.collect.Maps;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.actor.ActorConfig;
import lombok.val;

import java.util.Map;

import static io.appform.dropwizard.actors.utils.MessageHeaders.COMPRESSION_TYPE;
import static io.appform.dropwizard.actors.utils.MessageHeaders.DELAY;

public class PropertiesHelper {

    private final ActorConfig config;

    public PropertiesHelper(final ActorConfig config) {
        this.config = config;
    }

    public AMQP.BasicProperties createPropertiesWithDelay(final long delayMilliseconds,
                                                          final boolean compressionEnabled,
                                                          final boolean ttlDelayEnabled) {
        val properties = new AMQP.BasicProperties.Builder();
        val compressionHeader = addCompressionHeader(Maps.newHashMap(), compressionEnabled);
        val delayHeaders = addDelayHeader(compressionHeader, ttlDelayEnabled, delayMilliseconds);

        return properties.headers(delayHeaders)
                .deliveryMode(2)
                .expiration(ttlDelayEnabled ? String.valueOf(delayMilliseconds) : null)
                .build();
    }

    public AMQP.BasicProperties addMessageHeaders(final AMQP.BasicProperties properties,
                                                  final boolean compressionEnabled) {
        if (properties.getHeaders() == null) {
            // have to do this since AMQP.BasicProperties doesn't have any setter methods
            val headers = addCompressionHeader(Maps.newHashMap(), compressionEnabled);
            return new AMQP.BasicProperties.Builder()
                    .contentType(properties.getContentType())
                    .contentEncoding(properties.getContentEncoding())
                    .headers(headers)
                    .deliveryMode(properties.getDeliveryMode())
                    .priority(properties.getPriority())
                    .correlationId(properties.getCorrelationId())
                    .replyTo(properties.getReplyTo())
                    .expiration(properties.getExpiration())
                    .messageId(properties.getMessageId())
                    .timestamp(properties.getTimestamp())
                    .type(properties.getType())
                    .userId(properties.getUserId())
                    .appId(properties.getAppId())
                    .clusterId(properties.getClusterId())
                    .build();
        }
        addCompressionHeader(properties.getHeaders(), compressionEnabled);
        return properties;
    }

    private Map<String, Object> addCompressionHeader(final Map<String, Object> headers,
                                                     final boolean compressionEnabled) {
        if (compressionEnabled) {
            headers.put(COMPRESSION_TYPE, config.getCompressionConfig().getCompressionAlgorithm());
        }
        return headers;
    }

    private Map<String, Object> addDelayHeader(final Map<String, Object> headers,
                                               final boolean ttlDelayEnabled,
                                               final long delayMilliseconds) {
        if (!ttlDelayEnabled) {
            headers.put(DELAY, delayMilliseconds);
        }
        return headers;
    }

}
