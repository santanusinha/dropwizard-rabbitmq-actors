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
import io.appform.dropwizard.actors.base.UnmanagedConsumer;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.connectivity.strategy.ConnectionIsolationStrategy;
import io.appform.dropwizard.actors.connectivity.strategy.ConnectionIsolationStrategyVisitor;
import io.appform.dropwizard.actors.connectivity.strategy.DefaultConnectionStrategy;
import io.appform.dropwizard.actors.connectivity.strategy.SharedConnectionStrategy;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.NotImplementedException;

import java.util.function.Function;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * This actor can be derived to directly call start/stop. This is not Managed and will not be automatically started by
 * dropwizard.
 */
@Data
@EqualsAndHashCode
@ToString
@Slf4j
public class UnmanagedBaseActor<Message> {

    private final UnmanagedPublisher<Message> publishActor;
    private final UnmanagedConsumer<Message> consumeActor;

    public UnmanagedBaseActor(UnmanagedPublisher<Message> publishActor,
                              UnmanagedConsumer<Message> consumeActor) {
        this.publishActor = publishActor;
        this.consumeActor = consumeActor;
    }

    public UnmanagedBaseActor(
            String name,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            ExceptionHandlingFactory exceptionHandlingFactory,
            Class<? extends Message> clazz,
            MessageHandlingFunction<Message, Boolean> handlerFunction,
            MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
            Function<Throwable, Boolean> errorCheckFunction) {
        this(new UnmanagedPublisher<>(name, config, connection, mapper),
                new UnmanagedConsumer<>(
                        name, config, connection, mapper, retryStrategyFactory, exceptionHandlingFactory, clazz,
                        handlerFunction, expiredMessageHandlingFunction, errorCheckFunction));
    }

    public UnmanagedBaseActor(
            String name,
            ActorConfig config,
            ConnectionRegistry connectionRegistry,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            ExceptionHandlingFactory exceptionHandlingFactory,
            Class<? extends Message> clazz,
            MessageHandlingFunction<Message, Boolean> handlerFunction,
            MessageHandlingFunction<Message, Boolean> expiredMessageHandlingFunction,
            Function<Throwable, Boolean> errorCheckFunction) {
        val consumerConnection = connectionRegistry.createOrGet(consumerConnectionName(config.getConsumer()));
        val producerConnection = connectionRegistry.createOrGet(producerConnectionName(config.getProducer()));
        this.publishActor = new UnmanagedPublisher<>(name, config, producerConnection, mapper);
        this.consumeActor = new UnmanagedConsumer<>(
                name, config, consumerConnection, mapper, retryStrategyFactory, exceptionHandlingFactory, clazz,
                handlerFunction, expiredMessageHandlingFunction, errorCheckFunction);
    }

    public void start() throws Exception {
        if (nonNull(publishActor)) {
            publishActor.start();
        }
        if (nonNull(consumeActor)) {
            consumeActor.start();
        }
    }

    public void stop() throws Exception {
        if (nonNull(publishActor)) {
            publishActor.stop();
        }
        if (nonNull(consumeActor)) {
            consumeActor.stop();
        }
    }

    public final void publishWithDelay(final Message message, final long delayMilliseconds) throws Exception {
        publishActor().publishWithDelay(message, delayMilliseconds);
    }

    public final void publishWithExpiry(final Message message, final long expiryInMs) throws Exception {
        publishActor().publishWithExpiry(message, expiryInMs);
    }

    public final void publishWithDelayAndExpiry(final Message message,
                                                final long expiryInMs,
                                                final long delayMilliseconds) throws Exception {
        publishActor().publishWithDelayAndExpiry(message, expiryInMs, delayMilliseconds);
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

    private UnmanagedPublisher<Message> publishActor() {
        if (isNull(publishActor)) {
            throw new NotImplementedException("PublishActor is not initialized");
        }
        return publishActor;
    }

    private String producerConnectionName(ProducerConfig producerConfig) {
        if (producerConfig == null) {
            return Constants.DEFAULT_PRODUCER_CONNECTION_NAME;
        }
        return deriveConnectionName(producerConfig.getConnectionIsolationStrategy(), Constants.DEFAULT_PRODUCER_CONNECTION_NAME);
    }

    private String consumerConnectionName(ConsumerConfig consumerConfig) {
        if (consumerConfig == null) {
            return Constants.DEFAULT_CONSUMER_CONNECTION_NAME;
        }

        return deriveConnectionName(consumerConfig.getConnectionIsolationStrategy(), Constants.DEFAULT_CONSUMER_CONNECTION_NAME);
    }

    private String deriveConnectionName(ConnectionIsolationStrategy isolationStrategy, String defaultConnectionName) {
        if (isolationStrategy == null) {
            return defaultConnectionName;
        }

        return isolationStrategy.accept(new ConnectionIsolationStrategyVisitor<>() {

            @Override
            public String visit(SharedConnectionStrategy strategy) {
                return strategy.getName();
            }

            @Override
            public String visit(DefaultConnectionStrategy strategy) {
                return defaultConnectionName;
            }

        });
    }
}
