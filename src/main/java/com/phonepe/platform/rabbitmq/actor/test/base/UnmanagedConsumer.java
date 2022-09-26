package com.phonepe.platform.rabbitmq.actor.test.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.phonepe.platform.rabbitmq.actor.test.actor.ActorConfig;
import com.phonepe.platform.rabbitmq.actor.test.actor.MessageHandlingFunction;
import com.phonepe.platform.rabbitmq.actor.test.base.utils.NamingUtils;
import com.phonepe.platform.rabbitmq.actor.test.exceptionhandler.ExceptionHandlingFactory;
import com.rabbitmq.client.Channel;
import com.phonepe.platform.rabbitmq.actor.test.connectivity.RMQConnection;
import com.phonepe.platform.rabbitmq.actor.test.exceptionhandler.handlers.ExceptionHandler;
import com.phonepe.platform.rabbitmq.actor.test.retry.RetryStrategy;
import com.phonepe.platform.rabbitmq.actor.test.retry.RetryStrategyFactory;
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
    private final MessageHandlingFunction<Message, Boolean> handlerFunction;
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
                             final Function<Throwable, Boolean> errorCheckFunction) {
        this.name = NamingUtils.prefixWithNamespace(name);
        this.config = config;
        this.connection = connection;
        this.mapper = mapper;
        this.clazz = clazz;
        this.prefetchCount = config.getPrefetchCount();
        this.handlerFunction = handlerFunction;
        this.errorCheckFunction = errorCheckFunction;
        this.queueName = NamingUtils.queueName(config.getPrefix(), name);
        this.retryStrategy = retryStrategyFactory.create(config.getRetryConfig());
        this.exceptionHandler = exceptionHandlingFactory.create(config.getExceptionHandlerConfig());
    }

    public void start() throws Exception {
        for (int i = 1; i <= config.getConcurrency(); i++) {
            Channel consumeChannel = connection.newChannel();
            final Handler<Message> handler =
                    new Handler<>(consumeChannel, mapper, clazz, prefetchCount, errorCheckFunction, retryStrategy,
                                  exceptionHandler, handlerFunction);
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
                channel.basicCancel(handler.getTag());
                channel.close();
                log.info("Consumer channel closed for [{}] with prefix [{}]", name, config.getPrefix());
            } catch (Exception e) {
                log.error(String.format("Error closing consumer channel [%s] for [%s] with prefix [%s]", handler.getTag(), name, config.getPrefix()), e);
            }
        });
    }
}
