package io.appform.dropwizard.actors.base;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.appform.dropwizard.actors.common.Constants.MESSAGE_EXPIRY_TEXT;

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
    private volatile boolean running;

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

    private boolean handle(Message message, MessageMetadata messageMetadata, boolean expired) throws Exception {
        running = true;
        val result = expired
                ? expiredMessageHandlingFunction.apply(message, messageMetadata)
                : messageHandlingFunction.apply(message, messageMetadata);
        running = false;
        return result;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope,
                               AMQP.BasicProperties properties, byte[] body) throws IOException {
        try {
            val handleCallable = getHandleCallable(envelope, properties, body);

            if (retryStrategy.execute(handleCallable)) {
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

    private Callable<Boolean> getHandleCallable(Envelope envelope,
                                                AMQP.BasicProperties properties,
                                                byte[] body) throws IOException {
        val delayInMs = getDelayInMs(properties);
        val expired = isExpired(properties);
        val message = mapper.readValue(body, clazz);
        return () -> handle(message, messageProperties(envelope, delayInMs), expired);
    }

    private Long getDelayInMs(AMQP.BasicProperties properties) {
        return properties.getTimestamp() != null
                ? Instant.now().toEpochMilli() - properties.getTimestamp().getTime()
                : null;
    }

    private boolean isExpired(AMQP.BasicProperties properties) {
        if (properties.getHeaders().containsKey(MESSAGE_EXPIRY_TEXT)) {
            val expiresAt = (long) properties.getHeaders().get(MESSAGE_EXPIRY_TEXT);
            return Instant.now().toEpochMilli() >= expiresAt;
        }
        return false;
    }

    private MessageMetadata messageProperties(final Envelope envelope, Long messageDelay) {
        return new MessageMetadata(envelope.isRedeliver(), messageDelay);
    }
}