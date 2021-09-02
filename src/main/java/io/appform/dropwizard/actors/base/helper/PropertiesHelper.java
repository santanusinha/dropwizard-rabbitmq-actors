package io.appform.dropwizard.actors.base.helper;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.actor.ActorConfig;

import java.util.Map;

import static io.appform.dropwizard.actors.utils.MessageHeaders.COMPRESSION_TYPE;
import static io.appform.dropwizard.actors.utils.MessageHeaders.DELAY;

public class PropertiesHelper {

    private final ActorConfig config;

    public PropertiesHelper(final ActorConfig config) {
        this.config = config;
    }

    public Map<String, Object> addCompressionHeader(final Map<String, Object> headers,
                                                    final boolean compressionEnabled) {
        if (compressionEnabled) {
            headers.put(COMPRESSION_TYPE, config.getCompressionConfig().getCompressionAlgorithm());
        }
        return headers;
    }

    public Map<String, Object> addDelayHeader(final Map<String, Object> headers,
                                              final boolean ttlDelayEnabled,
                                              final long delayMilliseconds) {
        if (!ttlDelayEnabled) {
            headers.put(DELAY, delayMilliseconds);
        }
        return headers;
    }

    public AMQP.BasicProperties.Builder setDelayProperties(final AMQP.BasicProperties.Builder properties,
                                                           final boolean delay,
                                                           final long delayMilliseconds) {

        if(delay) {
            properties.expiration(String.valueOf(delayMilliseconds))
                    .deliveryMode(2);
        } else {
            properties.deliveryMode(2);
        }
        return properties;
    }
}
