package io.appform.dropwizard.actors.base;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import io.appform.dropwizard.actors.utils.CommonUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.appform.dropwizard.actors.common.Constants.MESSAGE_TYPE_TEXT;

@Slf4j
public class Handler<Message> extends DefaultConsumer {

    private final ObjectMapper mapper;
    private final Class<? extends Message> clazz;
    private final long messageExpiryInMs;
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
                   final long messageExpiryInMs,
                   final Function<Throwable, Boolean> errorCheckFunction,
                   final RetryStrategy retryStrategy,
                   final ExceptionHandler exceptionHandler,
                   final MessageHandlingFunction<Message, Boolean> messageHandlingFunction,
                   final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction) throws Exception {
        super(channel);
        this.mapper = mapper;
        this.clazz = clazz;
        getChannel().basicQos(prefetchCount);
        this.messageExpiryInMs = messageExpiryInMs;
        this.errorCheckFunction = errorCheckFunction;
        this.retryStrategy = retryStrategy;
        this.exceptionHandler = exceptionHandler;
        this.messageHandlingFunction = messageHandlingFunction;
        this.expiredMessageHandlingFunction = expiredMessageHandlingFunction;
    }

    private boolean handle(Message message, MessageMetadata messageMetadata) throws Exception {
        val isExpired = messageExpiryInMs != 0 && messageMetadata.getDelayInMs() != null &&
                messageMetadata.getDelayInMs() > messageExpiryInMs;
        return isExpired
                ? expiredMessageHandlingFunction.apply(message, messageMetadata)
                : messageHandlingFunction.apply(message, messageMetadata);
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope,
                               AMQP.BasicProperties properties, byte[] body) throws IOException {
        try {
            final Callable<Boolean> handleCallable = getHandleCallable(envelope, properties, body);

            boolean success = retryStrategy.execute(handleCallable);

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

    private Callable<Boolean> getHandleCallable(Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        val messageType = getMessageType(properties);
        return messageType.accept(new MessageType.MessageTypeVisitor<Callable<Boolean>, byte[]>() {
            @Override
            @SneakyThrows
            public Callable<Boolean> visitSimple(byte[] data) {
                final Message message = mapper.readValue(body, clazz);
                return () -> handle(message, messageProperties(envelope, null));
            }

            @Override
            @SneakyThrows
            public Callable<Boolean> visitWrapped(byte[] data) {
                val javaType = mapper.getTypeFactory().constructParametricType(MessageWrapper.class, clazz);
                MessageWrapper<Message> messageWrapper = mapper.readValue(body, javaType);
                val messageDelay = Instant.now().toEpochMilli() - messageWrapper.getPublishTimeStamp();
                return () -> handle(messageWrapper.getMessage(), messageProperties(envelope, messageDelay));
            }
        }, body);
    }

    private MessageType getMessageType(final AMQP.BasicProperties properties) {
        return CommonUtils.isEmpty(properties.getHeaders()) || !properties.getHeaders().containsKey(MESSAGE_TYPE_TEXT)
                ? MessageType.SIMPLE
                : MessageType.valueOf(properties.getHeaders().get(MESSAGE_TYPE_TEXT).toString());
    }

    private MessageMetadata messageProperties(final Envelope envelope, Long messageDelay) {
        return new MessageMetadata(envelope.isRedeliver(), messageDelay);
    }
}