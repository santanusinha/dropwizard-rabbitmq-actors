/*
 * Copyright (c) 2019 Santanu Sinha <santanu.sinha@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.appform.dropwizard.actors.actor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.base.RandomShardIdCalculator;
import io.appform.dropwizard.actors.base.ShardIdCalculator;
import io.appform.dropwizard.actors.base.UnmanagedConsumer;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import java.util.Objects;
import java.util.function.Function;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

/**
 * This actor can be derived to directly call start/stop. This is not Managed and will not be automatically started by
 * dropwizard.
 */
@Data
@Slf4j
@ToString
@EqualsAndHashCode
@SuppressWarnings("java:S107")
public class UnmanagedBaseActor<Message> {

    private final UnmanagedPublisher<Message> publishActor;
    private final UnmanagedConsumer<Message> consumeActor;
    private final UnmanagedConsumer<Message> sidelineProcessorActor;

    public UnmanagedBaseActor(final UnmanagedPublisher<Message> publishActor,
                              final UnmanagedConsumer<Message> consumeActor,
                              final UnmanagedConsumer<Message> sidelineProcessorActor) {
        this.publishActor = publishActor;
        this.consumeActor = consumeActor;
        this.sidelineProcessorActor = sidelineProcessorActor;
    }

    public UnmanagedBaseActor(final String name,
                              final ActorConfig config,
                              final RMQConnection connection,
                              final ObjectMapper mapper,
                              final RetryStrategyFactory retryStrategyFactory,
                              final ExceptionHandlingFactory exceptionHandlingFactory,
                              final Class<? extends Message> clazz,
                              final MessageHandlingFunction<Message, Boolean> handlerFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorHandleFunction,
                              final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorExpiredMessageHandlingFunction,
                              final Function<Throwable, Boolean> errorCheckFunction) {
        this(name, config, connection, mapper, new RandomShardIdCalculator<>(config), retryStrategyFactory,
                exceptionHandlingFactory, clazz, handlerFunction, sidelineProcessorHandleFunction,
                expiredMessageHandlingFunction, sidelineProcessorExpiredMessageHandlingFunction, errorCheckFunction);
    }

    public UnmanagedBaseActor(final String name,
                              final ActorConfig config,
                              final RMQConnection connection,
                              final ObjectMapper mapper,
                              final ShardIdCalculator<Message> shardIdCalculator,
                              final RetryStrategyFactory retryStrategyFactory,
                              final ExceptionHandlingFactory exceptionHandlingFactory,
                              final Class<? extends Message> clazz,
                              final MessageHandlingFunction<Message, Boolean> handlerFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorHandleFunction,
                              final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorExpiredMessageHandlingFunction,
                              final Function<Throwable, Boolean> errorCheckFunction) {
        this(name, config, connection, connection, connection, mapper, shardIdCalculator, retryStrategyFactory,
                exceptionHandlingFactory, clazz, handlerFunction, sidelineProcessorHandleFunction,
                expiredMessageHandlingFunction, sidelineProcessorExpiredMessageHandlingFunction, errorCheckFunction);
    }

    public UnmanagedBaseActor(final String name,
                              final ActorConfig config,
                              final ConnectionRegistry connectionRegistry,
                              final ObjectMapper mapper,
                              final RetryStrategyFactory retryStrategyFactory,
                              final ExceptionHandlingFactory exceptionHandlingFactory,
                              final Class<? extends Message> clazz,
                              final MessageHandlingFunction<Message, Boolean> handlerFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorHandleFunction,
                              final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorExpiredMessageHandlingFunction,
                              final Function<Throwable, Boolean> errorCheckFunction) {
        this(name, config, connectionRegistry, mapper, new RandomShardIdCalculator<>(config), retryStrategyFactory,
                exceptionHandlingFactory, clazz, handlerFunction, sidelineProcessorHandleFunction,
                expiredMessageHandlingFunction, sidelineProcessorExpiredMessageHandlingFunction, errorCheckFunction);
    }

    public UnmanagedBaseActor(final String name,
                              final ActorConfig config,
                              final ConnectionRegistry connectionRegistry,
                              final ObjectMapper mapper,
                              final ShardIdCalculator<Message> shardIdCalculator,
                              final RetryStrategyFactory retryStrategyFactory,
                              final ExceptionHandlingFactory exceptionHandlingFactory,
                              final Class<? extends Message> clazz,
                              final MessageHandlingFunction<Message, Boolean> handlerFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorHandleFunction,
                              final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorExpiredMessageHandlingFunction,
                              final Function<Throwable, Boolean> errorCheckFunction) {

        this(name, config, connectionRegistry.createOrGet(NamingUtils.producerConnectionName(config.getProducer())),
                connectionRegistry.createOrGet(NamingUtils.consumerConnectionName(config.getConsumer())),
                connectionRegistry.createOrGet(NamingUtils.sidelineProcessorConnectionName(
                        config.getSidelineProcessorConfig())), mapper,
                shardIdCalculator, retryStrategyFactory, exceptionHandlingFactory, clazz, handlerFunction,
                sidelineProcessorHandleFunction, expiredMessageHandlingFunction,
                sidelineProcessorExpiredMessageHandlingFunction, errorCheckFunction);
    }

    public UnmanagedBaseActor(final String name,
                              final ActorConfig config,
                              final RMQConnection producerConnection,
                              final RMQConnection consumerConnection,
                              final RMQConnection sidelineProcessorConnection,
                              final ObjectMapper mapper,
                              final ShardIdCalculator<Message> shardIdCalculator,
                              final RetryStrategyFactory retryStrategyFactory,
                              final ExceptionHandlingFactory exceptionHandlingFactory,
                              final Class<? extends Message> clazz,
                              final MessageHandlingFunction<Message, Boolean> handlerFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorHandleFunction,
                              final MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
                              final MessageHandlingFunction<Message, Boolean> sidelineProcessorExpiredMessageHandlingFunction,
                              final Function<Throwable, Boolean> errorCheckFunction) {

        this(new UnmanagedPublisher<>(NamingUtils.queueName(config.getPrefix(), name), config, shardIdCalculator,
                        producerConnection, mapper),
                new UnmanagedConsumer<>(NamingUtils.queueName(config.getPrefix(), name), config.getPrefetchCount(),
                        config.getConcurrency(), config.isSharded(), config.getShardCount(), config.getConsumer(),
                        consumerConnection, mapper, retryStrategyFactory.create(config.getRetryConfig()),
                        exceptionHandlingFactory.create(config.getExceptionHandlerConfig()), clazz, handlerFunction,
                        expiredMessageHandlingFunction, errorCheckFunction), config.isSidelineProcessorEnabled()
                                                                             ? new UnmanagedConsumer<>(
                        NamingUtils.sidelineProcessorQueueName(config.getPrefix(), name), config.getPrefetchCount(),
                        config.getSidelineProcessorConfig()
                                .getConcurrency(), config.isSharded(), config
                        .getShardCount(), config.getSidelineProcessorConfig()
                        .getConsumerConfig(),
                        sidelineProcessorConnection, mapper, retryStrategyFactory.create(
                        config.getSidelineProcessorConfig()
                                .getRetryConfig()),
                        exceptionHandlingFactory.create(config.getExceptionHandlerConfig()), clazz,
                        sidelineProcessorHandleFunction, sidelineProcessorExpiredMessageHandlingFunction,
                        errorCheckFunction)
                                                                             : null);
    }

    public void start() throws Exception {
        if (Objects.nonNull(publishActor)) {
            publishActor.start();
        }
        if (Objects.nonNull(consumeActor)) {
            consumeActor.start();
        }
        if (Objects.nonNull(sidelineProcessorActor)) {
            sidelineProcessorActor.start();
        }
    }

    public void stop() throws Exception {
        if (Objects.nonNull(publishActor)) {
            publishActor.stop();
        }
        if (Objects.nonNull(consumeActor)) {
            consumeActor.stop();
        }
        if (Objects.nonNull(sidelineProcessorActor)) {
            sidelineProcessorActor.stop();
        }
    }

    public final void publishWithDelay(final Message message, final long delayMilliseconds) throws Exception {
        publishActor().publishWithDelay(message, delayMilliseconds);
    }

    public final void publishWithExpiry(final Message message, final long expiryInMs) throws Exception {
        publishActor().publishWithExpiry(message, expiryInMs);
    }

    public final void publish(final Message message) throws Exception {
        publishActor().publish(message);
    }

    public final void publish(final Message message, final AMQP.BasicProperties properties) throws Exception {
        publishActor().publish(message, properties);
    }

    public final long pendingMessagesCount() {
        return publishActor().pendingMessagesCount();
    }

    public final long pendingSidelineMessagesCount() {
        return publishActor().pendingSidelineMessagesCount();
    }

    public final long pendingSidelineProcessorMessagesCount() {
        return publishActor().pendingSidelineProcessorMessagesCount();
    }

    private UnmanagedPublisher<Message> publishActor() {
        if (Objects.isNull(publishActor)) {
            throw new NotImplementedException("PublishActor is not initialized");
        }
        return publishActor;
    }
}
