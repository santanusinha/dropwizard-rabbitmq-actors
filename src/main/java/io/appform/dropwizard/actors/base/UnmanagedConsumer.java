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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class UnmanagedConsumer<Message> {

    private final String name;
    private final Supplier<ActorConfig> configSupplier;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final Class<? extends Message> clazz;
    private final MessageHandlingFunction<Message, Boolean> handlerFunction;
    private final Function<Throwable, Boolean> errorCheckFunction;
    private final String queueName;
    private final RetryStrategy retryStrategy;
    private final ExceptionHandler exceptionHandler;
    private final List<Handler<Message>> handlers = Collections.synchronizedList(Lists.newArrayList());
    private final ScheduledExecutorService consumerRefresher;

    public UnmanagedConsumer(final String name,
                             final Supplier<ActorConfig> configSupplier,
                             final RMQConnection connection,
                             final ObjectMapper mapper,
                             final RetryStrategyFactory retryStrategyFactory,
                             final ExceptionHandlingFactory exceptionHandlingFactory,
                             final Class<? extends Message> clazz,
                             final MessageHandlingFunction<Message, Boolean> handlerFunction,
                             final Function<Throwable, Boolean> errorCheckFunction) {
        this.name = NamingUtils.prefixWithNamespace(name);
        this.configSupplier = configSupplier;
        this.connection = connection;
        this.mapper = mapper;
        this.clazz = clazz;
        this.handlerFunction = handlerFunction;
        this.errorCheckFunction = errorCheckFunction;
        this.queueName = NamingUtils.queueName(configSupplier.get().getPrefix(), name);
        this.retryStrategy = retryStrategyFactory.create(configSupplier.get().getRetryConfig());
        this.exceptionHandler = exceptionHandlingFactory.create(configSupplier.get().getExceptionHandlerConfig());
        this.consumerRefresher = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() throws Exception {
        final ActorConfig config = configSupplier.get();
        this.consumerRefresher.scheduleWithFixedDelay(() -> {
            try {
                refreshHandlers();
            } catch (Exception e) {
                log.error("Consumer list refresh failed for [{}] with prefix [{}]", name, config.getPrefix());
            }
        }, 30, 30, TimeUnit.SECONDS);
        for (int i = 1; i <= config.getConcurrency(); i++) {
            createHandler();
        }
    }

    public void stop() {
        handlers.forEach(this::destroyHandler);
        this.consumerRefresher.shutdown();
    }

    private void createHandler() throws Exception {
        final ActorConfig config = configSupplier.get();
        final Channel consumeChannel = connection.newChannel();
        final Handler<Message> handler =
                new Handler<>(consumeChannel, mapper, clazz, config.getPrefetchCount(), errorCheckFunction, retryStrategy,
                        exceptionHandler, handlerFunction);
        String queueNameForConsumption;
        if (config.isSharded()) {
            queueNameForConsumption = NamingUtils.getShardedQueueName(queueName,
                    handlers.size() % config.getShardCount());
        } else {
            queueNameForConsumption = queueName;
        }
        final String tag = consumeChannel.basicConsume(queueNameForConsumption, false, handler);
        handler.setTag(tag);
        handlers.add(handler);
        log.info("Consumer channel started for [{}] with prefix [{}]", name, config.getPrefix());
    }

    private void destroyHandler(final Handler<Message> handler) {
        final ActorConfig config = configSupplier.get();
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
    }

    private synchronized void refreshHandlers() throws Exception {
        final ActorConfig config = configSupplier.get();
        if (handlers.size() == config.getConcurrency()) {
            log.info("Current handler list has same size as concurrency for [{}] with prefix [{}]", name,
                    config.getPrefix());
            return;
        }

        if (handlers.size() < config.getConcurrency()) {
            int consumersToBeAdded = config.getConcurrency() - handlers.size();
            for (int i = 0; i < consumersToBeAdded; i++) {
                createHandler();
            }
        } else {
            int consumersToBeRemoved = handlers.size() - config.getConcurrency();
            for (int i = 0; i < consumersToBeRemoved; i++) {
                final Handler<Message> handler = handlers.remove(0);
                destroyHandler(handler);
            }
        }
    }
}
