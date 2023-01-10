package io.appform.dropwizard.actors.base;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.util.function.Function;

@Slf4j
public class Handler<Message> extends DefaultConsumer {

    private final ObjectMapper mapper;
    private final Class<? extends Message> clazz;
    private final Function<Throwable, Boolean> errorCheckFunction;
    private final RetryStrategy retryStrategy;
    private final ExceptionHandler exceptionHandler;
    private final MessageHandlingFunction<Message, Boolean> messageHandlingFunction;
    private final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction;

    @Getter
    @Setter
    private String tag;

    public Handler(final Channel channel,
                   final ObjectMapper mapper,
                   final Class<? extends Message> clazz,
                   final int prefetchCount,
                   final Function<Throwable, Boolean> errorCheckFunction,
                   final RetryStrategy retryStrategy,
                   final ExceptionHandler exceptionHandler,
                   final MessageHandlingFunction<Message, Boolean> messageHandlingFunction,
                   final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction) throws Exception {
        super(channel);
        this.mapper = mapper;
        this.clazz = clazz;
        getChannel().basicQos(prefetchCount);
        this.errorCheckFunction = errorCheckFunction;
        this.retryStrategy = retryStrategy;
        this.exceptionHandler = exceptionHandler;
        this.messageHandlingFunction = messageHandlingFunction;
        this.expiredMessageHandlingFunction = expiredMessageHandlingFunction;
    }

    private boolean handle(Message message, MessageMetadata messageMetadata) throws Exception {
        val isExpired = messageMetadata.getValidTill() > 0
                && messageMetadata.getValidTill() < System.currentTimeMillis();
        return isExpired
                ? expiredMessageHandlingFunction.apply(message, messageMetadata)
                : messageHandlingFunction.apply(message, messageMetadata);
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope,
                               AMQP.BasicProperties properties, byte[] body) throws IOException {
        try {
            final Message message = mapper.readValue(body, clazz);
            boolean success = retryStrategy.execute(() -> handle(message, messageProperties(envelope, properties)));
            if (success) {
                getChannel().basicAck(envelope.getDeliveryTag(), false);
            } else {
                getChannel().basicReject(envelope.getDeliveryTag(), false);
            }
        } catch (Throwable t) {
            log.error("Error processing message...", t);
            if (errorCheckFunction.apply(t)) {
                log.warn("Acked message due to exception: ", t);
                getChannel().basicAck(envelope.getDeliveryTag(), false);
            } else if (exceptionHandler.handle()) {
                log.warn("Acked message due to exception handling strategy: ", t);
                getChannel().basicAck(envelope.getDeliveryTag(), false);
            } else {
                getChannel().basicReject(envelope.getDeliveryTag(), false);
            }
        }
    }

    private MessageMetadata messageProperties(final Envelope envelope,
                                              final AMQP.BasicProperties properties) {
        long validTill = (long) properties.getHeaders().getOrDefault(Constants.MESSAGE_VALIDITY_PROPERTY, 0L);
        return new MessageMetadata(envelope.isRedeliver(), validTill);
    }
}