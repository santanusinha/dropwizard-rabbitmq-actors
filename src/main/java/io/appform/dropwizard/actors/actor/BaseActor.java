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
import com.rabbitmq.client.MessageProperties;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.base.UnmanagedConsumer;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.dropwizard.lifecycle.Managed;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;

import java.util.Collections;
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
                this::isExceptionIgnorable);
    }

    abstract protected boolean handle(Message message) throws Exception;

    protected boolean isExceptionIgnorable(Throwable t) {
        return droppedExceptionTypes
                .stream()
                .anyMatch(exceptionType -> ClassUtils.isAssignable(t.getClass(), exceptionType));
    }

    public final void publishWithDelay(Message message, long delayMilliseconds) throws Exception {
        actorImpl.publishWithDelay(message, delayMilliseconds);
    }

    public final void publish(Message message) throws Exception {
        publish(message, MessageProperties.MINIMAL_PERSISTENT_BASIC);
    }

    public final void publish(Message message, AMQP.BasicProperties properties) throws Exception {
        actorImpl.publish(message, properties);
    }

    public final long pendingMessagesCount() {
        return actorImpl.pendingMessagesCount();
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
