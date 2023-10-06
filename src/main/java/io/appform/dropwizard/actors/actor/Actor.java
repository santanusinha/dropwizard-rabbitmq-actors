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
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * A simpler derivation of {@link BaseActor} to be used in most common actor use cases. This is managed by dropwizard.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Slf4j
public abstract class Actor<MessageType extends Enum<MessageType>, Message> extends BaseActor<Message> {

    private MessageType type;

    @Deprecated
    protected Actor(
            MessageType type,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            ExceptionHandlingFactory exceptionHandlingFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        super(type.name(), config, connection, mapper, retryStrategyFactory, exceptionHandlingFactory,
                clazz, droppedExceptionTypes);
        this.type = type;
    }

    protected Actor(
            MessageType type,
            ActorConfig config,
            ConnectionRegistry connectionRegistry,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            ExceptionHandlingFactory exceptionHandlingFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        this(type, StringUtils.EMPTY, config, connectionRegistry, mapper, retryStrategyFactory, exceptionHandlingFactory,
                clazz, droppedExceptionTypes);
    }

    protected Actor(
            MessageType type,
            String consumerTag,
            ActorConfig config,
            ConnectionRegistry connectionRegistry,
            ObjectMapper mapper,
            RetryStrategyFactory retryStrategyFactory,
            ExceptionHandlingFactory exceptionHandlingFactory,
            Class<? extends Message> clazz,
            Set<Class<?>> droppedExceptionTypes) {
        super(type.name(), consumerTag, config, connectionRegistry, mapper, retryStrategyFactory, exceptionHandlingFactory,
                clazz, droppedExceptionTypes);
        this.type = type;
    }
}
