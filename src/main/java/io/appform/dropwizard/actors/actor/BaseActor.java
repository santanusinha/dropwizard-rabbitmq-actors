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
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.dropwizard.lifecycle.Managed;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.ClassUtils;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * This is a managed wrapper for {@link UnmanagedBaseActor} this is managed and therefore started by D/W.
 */
@Data
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class BaseActor<Message> implements Managed {

    private final UnmanagedBaseActor<Message> actorImpl;
    private final Set<Class<?>> droppedExceptionTypes;

    protected BaseActor(UnmanagedPublisher<Message> publishActor, Set<Class<?>> droppedExceptionTypes) {
        this(publishActor, null, droppedExceptionTypes);
    }

    protected BaseActor(UnmanagedConsumer<Message> consumeActor, Set<Class<?>> droppedExceptionTypes) {
        this(null, consumeActor, droppedExceptionTypes);
    }

    protected BaseActor(UnmanagedPublisher<Message> produceActor,
                        UnmanagedConsumer<Message> consumeActor,
                        Set<Class<?>> droppedExceptionTypes) {
        actorImpl = new UnmanagedBaseActor<>(produceActor, consumeActor);
        this.droppedExceptionTypes = droppedExceptionTypes;
    }

    @Deprecated
    protected BaseActor(
            String name,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            ExceptionHandlingFactory exceptionHandlingFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        this.droppedExceptionTypes
                = null == droppedExceptionTypes
                ? Collections.emptySet() : droppedExceptionTypes;
        actorImpl = new UnmanagedBaseActor<>(name, config, connection, mapper, retryStrategyFactory,
                exceptionHandlingFactory, clazz,
                this::handle,
                this::handleExpiredMessages,
                this::isExceptionIgnorable);
    }

    protected BaseActor(
            String name,
            ActorConfig config,
            ConnectionRegistry connectionRegistry,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            ExceptionHandlingFactory exceptionHandlingFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        this.droppedExceptionTypes
                = null == droppedExceptionTypes
                ? Collections.emptySet() : droppedExceptionTypes;
        actorImpl = new UnmanagedBaseActor<>(name, config, connectionRegistry, mapper, retryStrategyFactory,
                exceptionHandlingFactory, clazz,
                this::handle,
                this::handleExpiredMessages,
                this::isExceptionIgnorable);
    }

    /*
        Override this method in your code for custom implementation.
     */
    protected boolean handle(Message message, MessageMetadata messageMetadata) throws Exception {
        return handle(message);
    }

    /*
        Override this method in your code in case you want to handle the expired messages separately
     */
    protected boolean handleExpiredMessages(Message message, MessageMetadata messageMetadata) throws Exception {
        return true;
    }

    /*
        Override this method in your code for custom implementation.
     */
    @Deprecated
    protected boolean handle(Message message) throws Exception {
        throw new UnsupportedOperationException("Either implement this method, or implement the handle(message, messageMetadata) method");
    }

    protected boolean isExceptionIgnorable(Throwable t) {
        return droppedExceptionTypes
                .stream()
                .anyMatch(exceptionType -> ClassUtils.isAssignable(t.getClass(), exceptionType));
    }

    public final void publishWithDelay(final Message message, final long delayMilliseconds) throws Exception {
        actorImpl.publishWithDelay(message, delayMilliseconds);
    }

    public final void publishWithExpiry(final Message message, final long expiryInMs) throws Exception {
        actorImpl.publishWithExpiry(message, expiryInMs);
    }

    public final void publishWithDelayAndExpiry(final Message message,
                                                final long expiryInMs,
                                                final long delayMilliseconds) throws Exception {
        actorImpl.publishWithDelayAndExpiry(message, expiryInMs, delayMilliseconds);
    }

    public final void publish(final Message message) throws Exception {
        val properties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .timestamp(new Date())
                .build();
        publish(message, properties);
    }

    public final void publish(final Message message, final AMQP.BasicProperties properties) throws Exception {
        actorImpl.publish(message, properties);
    }

    public final long pendingMessagesCount() {
        return actorImpl.pendingMessagesCount();
    }

    public final long pendingSidelineMessagesCount() {
        return actorImpl.pendingSidelineMessagesCount();
    }

    @Override
    public void start() throws Exception {
        actorImpl.start();
    }

    @Override
    public void stop() throws Exception {
        actorImpl.stop();
    }
}
