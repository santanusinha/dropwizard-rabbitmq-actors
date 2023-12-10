package io.appform.dropwizard.actors.actor.metadata;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.generators.MessageMetadataGenerator;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import lombok.val;

public class MessageMetadataProvider {

    private final List<MessageMetadataGenerator> messageMetadataGenerators;

    public MessageMetadataProvider(List<String> messageMetaGeneratorClasses) {
        messageMetadataGenerators = configureMessageMetadataGenerators(messageMetaGeneratorClasses);
    }

    public MessageMetadata createMetadata(final Envelope envelope, final AMQP.BasicProperties properties) {
        Objects.requireNonNull(envelope, "Envelope should not be null");
        Objects.requireNonNull(properties, "AMQP.BasicProperties should not be null");
        val messageMetaContext = MessageMetaContext.builder()
                .redelivered(envelope.isRedeliver())
                .contentType(properties.getContentType())
                .contentEncoding(properties.getContentEncoding())
                .headers(properties.getHeaders())
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

        val messageMetadata = MessageMetadata.builder().build();
        messageMetadataGenerators.forEach(generator -> generator.generate(messageMetaContext, messageMetadata));
        return messageMetadata;
    }

    private List<MessageMetadataGenerator> configureMessageMetadataGenerators(List<String> messageMetaGeneratorClasses) {
        if (null == messageMetaGeneratorClasses) {
            return List.of();
        }

        return messageMetaGeneratorClasses.stream()
                .distinct()
                .map(className -> {
                    try {
                        return (MessageMetadataGenerator) Class.forName(className)
                                .getDeclaredConstructor()
                                .newInstance();
                    } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                             IllegalAccessException | InvocationTargetException e) {
                        throw RabbitmqActorException.propagate("Failed to initialize messageMetadataGenerator "
                                + className, e);
                    }
                })
                .toList();
    }
}