package io.appform.dropwizard.actors.base;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import io.appform.dropwizard.actors.tracing.HeadersMapExtractAdapter;
import io.appform.dropwizard.actors.tracing.HeadersMapInjectAdapter;
import io.appform.dropwizard.actors.tracing.SpanDecorator;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.tracing.TracingHandler;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
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
                   final MessageHandlingFunction<Message, Boolean> messageHandlingFunction) throws Exception {
        super(channel);
        this.mapper = mapper;
        this.clazz = clazz;
        getChannel().basicQos(prefetchCount);
        this.errorCheckFunction = errorCheckFunction;
        this.retryStrategy = retryStrategy;
        this.exceptionHandler = exceptionHandler;
        this.messageHandlingFunction = messageHandlingFunction;
    }

    private boolean handle(Message message, MessageMetadata messageMetadata) throws Exception {
        return messageHandlingFunction.apply(message, messageMetadata);
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope,
                               AMQP.BasicProperties properties, byte[] body) throws IOException {
        try {
            final Message message = mapper.readValue(body, clazz);
            val tracer = TracingHandler.getTracer();
            val childSpan = TracingHandler.buildChildSpan(properties, tracer);
            log.debug("publishing message with traceId: {}, spanId: {}", childSpan.context().toTraceId(), childSpan.context().toSpanId());

            val scope = TracingHandler.activateSpan(tracer, childSpan);
            try {
                boolean success = retryStrategy.execute(() -> handle(message, messageProperties(envelope)));

                if (success) {
                    getChannel().basicAck(envelope.getDeliveryTag(), false);
                } else {
                    getChannel().basicReject(envelope.getDeliveryTag(), false);
                }
            } finally {
                TracingHandler.closeScopeAndSpan(childSpan,scope);
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

    private MessageMetadata messageProperties(final Envelope envelope) {
        return new MessageMetadata(envelope.isRedeliver());
    }
}