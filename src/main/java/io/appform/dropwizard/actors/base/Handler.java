package io.appform.dropwizard.actors.base;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetadataProvider;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class Handler<Message> extends DefaultConsumer {

    private final ObjectMapper mapper;
    private final Class<? extends Message> clazz;
    private final Function<Throwable, Boolean> errorCheckFunction;
    private final RetryStrategy retryStrategy;
    private final ExceptionHandler exceptionHandler;
    private final MessageHandlingFunction<Message, Boolean> messageHandlingFunction;
    private final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction;
    private final MessageMetadataProvider messageMetadataProvider;
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
                   final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
                   final MessageMetadataProvider messageMetadataProvider) throws Exception {
        super(channel);
        this.mapper = mapper;
        this.clazz = clazz;
        getChannel().basicQos(prefetchCount);
        this.errorCheckFunction = errorCheckFunction;
        this.retryStrategy = retryStrategy;
        this.exceptionHandler = exceptionHandler;
        this.messageHandlingFunction = messageHandlingFunction;
        this.expiredMessageHandlingFunction = expiredMessageHandlingFunction;
        this.messageMetadataProvider = messageMetadataProvider;
    }

    private boolean handle(final Message message, final MessageMetadata messageMetadata) throws Exception {
        running = true;
        try {
            return messageMetadata.isExpired()
                    ? expiredMessageHandlingFunction.apply(message, messageMetadata)
                    : messageHandlingFunction.apply(message, messageMetadata);
        } finally {
            running = false;
        }
    }

    @Override
    public void handleDelivery(final String consumerTag,
                               final Envelope envelope,
                               final AMQP.BasicProperties properties,
                               final byte[] body) throws IOException {
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

    private Callable<Boolean> getHandleCallable(final Envelope envelope,
                                                final AMQP.BasicProperties properties,
                                                final byte[] body) throws IOException {
        val message = mapper.readValue(body, clazz);
        return () -> handle(message, messageMetadataProvider.createMetadata(envelope, properties));
    }

}