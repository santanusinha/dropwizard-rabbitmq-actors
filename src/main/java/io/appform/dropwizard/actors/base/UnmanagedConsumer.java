package io.appform.dropwizard.actors.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;

@Slf4j
public class UnmanagedConsumer<Message> {

    private final String name;
    private final ActorConfig config;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final Class<? extends Message> clazz;
    private final int prefetchCount;
    private final long expiryInMs;
    private final MessageHandlingFunction<Message, Boolean> handlerFunction;
    private final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction;
    private final Function<Throwable, Boolean> errorCheckFunction;
    private final String queueName;
    private final RetryStrategy retryStrategy;
    private final ExceptionHandler exceptionHandler;

    private final List<Handler<Message>> handlers = Lists.newArrayList();

    public UnmanagedConsumer(final String name,
                             final ActorConfig config,
                             final RMQConnection connection,
                             final ObjectMapper mapper,
                             final RetryStrategyFactory retryStrategyFactory,
                             final ExceptionHandlingFactory exceptionHandlingFactory,
                             final Class<? extends Message> clazz,
                             final MessageHandlingFunction<Message, Boolean> handlerFunction,
                             final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
                             final Function<Throwable, Boolean> errorCheckFunction) {
        this.name = NamingUtils.prefixWithNamespace(name);
        this.config = config;
        this.connection = connection;
        this.mapper = mapper;
        this.clazz = clazz;
        this.prefetchCount = config.getPrefetchCount();
        this.expiryInMs = config.getExpiryInMs();
        this.handlerFunction = handlerFunction;
        this.expiredMessageHandlingFunction = expiredMessageHandlingFunction;
        this.errorCheckFunction = errorCheckFunction;
        this.queueName = NamingUtils.queueName(config.getPrefix(), name);
        this.retryStrategy = retryStrategyFactory.create(config.getRetryConfig());
        this.exceptionHandler = exceptionHandlingFactory.create(config.getExceptionHandlerConfig());
    }

    public void start() throws Exception {
        for (int i = 1; i <= config.getConcurrency(); i++) {
            Channel consumeChannel = connection.newChannel();
            final Handler<Message> handler =
                    new Handler<>(consumeChannel, mapper, clazz, prefetchCount, expiryInMs, errorCheckFunction, retryStrategy,
                                  exceptionHandler, handlerFunction, expiredMessageHandlingFunction);
            String queueNameForConsumption;
            if (config.isSharded()) {
                queueNameForConsumption = NamingUtils.getShardedQueueName(queueName, i % config.getShardCount());
            } else {
                queueNameForConsumption = queueName;
            }
            final String tag = consumeChannel.basicConsume(queueNameForConsumption, false, handler);
            handler.setTag(tag);
            handlers.add(handler);
            log.info("Started consumer {} of type {}", i, name);
        }
    }

    public void stop() {
        handlers.forEach(handler -> {
            try {
                final Channel channel = handler.getChannel();
                if(channel.isOpen()) {
                    channel.basicCancel(handler.getTag());
                    //Wait till the handler completes consuming and ack'ing the current message.
                    log.info("Waiting for handler to complete processing the current message..");
                    while(handler.isRunning());
                    channel.close();
                    log.info("Consumer channel closed for [{}] with prefix [{}]", name, config.getPrefix());
                } else {
                    log.warn("Consumer channel already closed for [{}] with prefix [{}]", name, config.getPrefix());
                }
            } catch (Exception e) {
                log.error(String.format("Error closing consumer channel [%s] for [%s] with prefix [%s]", handler.getTag(), name, config.getPrefix()), e);
            }
        });
    }
}
