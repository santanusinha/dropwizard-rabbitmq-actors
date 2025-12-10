package io.appform.dropwizard.actors.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.MessageHandlingFunction;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.handlers.ExceptionHandler;
import io.appform.dropwizard.actors.observers.RMQObserver;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Slf4j
public class UnmanagedConsumer<Message> {

    @Getter
    private final String queueName;
    private final int prefetchCount;
    private final int concurrency;
    private final boolean isSharded;
    private final int shardCount;
    private final ConsumerConfig consumerConfig;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final Class<? extends Message> clazz;
    private final MessageHandlingFunction<Message, Boolean> handlerFunction;
    private final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction;
    private final Function<Throwable, Boolean> errorCheckFunction;
    private final RetryStrategy retryStrategy;
    private final ExceptionHandler exceptionHandler;
    private final RMQObserver observer;
    private final List<Handler<Message>> handlers = new CopyOnWriteArrayList<>();
    private int consumerIndex = 1;

    public UnmanagedConsumer(final String queueName,
                             final int prefetchCount,
                             final int concurrency,
                             final boolean isSharded,
                             final int shardCount,
                             final ConsumerConfig consumerConfig,
                             final RMQConnection connection,
                             final ObjectMapper mapper,
                             final RetryStrategy retryStrategy,
                             final ExceptionHandler exceptionHandler,
                             final Class<? extends Message> clazz,
                             final MessageHandlingFunction<Message, Boolean> handlerFunction,
                             final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
                             final Function<Throwable, Boolean> errorCheckFunction) {
        this.connection = connection;
        this.mapper = mapper;
        this.clazz = clazz;
        this.prefetchCount = prefetchCount;
        this.handlerFunction = handlerFunction;
        this.expiredMessageHandlingFunction = expiredMessageHandlingFunction;
        this.errorCheckFunction = errorCheckFunction;
        this.concurrency = concurrency;
        this.isSharded = isSharded;
        this.shardCount = shardCount;
        this.consumerConfig = consumerConfig;
        this.queueName = queueName;
        this.retryStrategy = retryStrategy;
        this.exceptionHandler = exceptionHandler;
        this.observer = connection.getRootObserver();
    }

    public void start() throws Exception {
        for (int i = 1; i <= concurrency; i++) {
            String queueNameForConsumption;
            if (isSharded) {
                queueNameForConsumption = NamingUtils.getShardedQueueName(queueName, i % shardCount);
            } else {
                queueNameForConsumption = queueName;
            }
            createChannel(queueNameForConsumption);
        }
    }

    public void stop() {
        handlers.forEach(handler -> {
            try {
                final Channel channel = handler.getChannel();
                if (channel.isOpen()) {
                    channel.basicCancel(handler.getTag());
                    //Wait till the handler completes consuming and ack'ing the current message.
                    log.info("Waiting for handler to complete processing the current message..");
                    while (handler.isRunning()) {
                        // wait for the handler to complete processing the current message
                    }
                    channel.close();
                    log.info("Consumer channel closed for queue [{}]", queueName);
                } else {
                    log.warn("Consumer channel already closed for queue [{}]", queueName);
                }
            } catch (final Exception e) {
                log.error(String.format("Error closing consumer channel [%s] for queue [%s]", handler.getTag(),
                        queueName), e);
            }
        });
    }

    private String getConsumerTag(int consumerIndex) {
        return Optional.ofNullable(consumerConfig)
                .map(ConsumerConfig::getTagPrefix)
                .filter(StringUtils::isNotBlank)
                .map(tagPrefix -> tagPrefix + "_" + consumerIndex)
                .orElse(StringUtils.EMPTY);
    }

    private void channelClosedHandler(String queueName) {
        try {
            log.info("Re-creating channel for queue {} because a channel got closed", queueName);
            createChannel(queueName);
        } catch (Exception e) {
            throw RabbitmqActorException.propagate(e);
        }
    }

    private void createChannel(String queueName) throws Exception {
        Channel consumeChannel = connection.newChannel();
        consumeChannel.addShutdownListener(shutdownSignalException -> {
            // This needs to be run in a separate thread to avoid deadlock
            CompletableFuture.runAsync(() -> {
                log.error("Channel shutdown signal exception received for queue {}", queueName,
                        shutdownSignalException);
                channelClosedHandler(queueName);
            });
        });
        final Handler<Message> handler = new Handler<>(consumeChannel, mapper, clazz, prefetchCount, errorCheckFunction,
                retryStrategy, exceptionHandler, handlerFunction, expiredMessageHandlingFunction, observer, queueName);
        final String tag = consumeChannel.basicConsume(queueName, false, getConsumerTag(consumerIndex++), handler);
        handler.setTag(tag);
        handlers.add(handler);
        log.info("Started consumer for queue {} with tag {}", queueName, tag);
    }

}
