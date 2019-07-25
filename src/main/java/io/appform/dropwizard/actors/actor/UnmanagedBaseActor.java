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

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.dropwizard.actors.actor.UnmanagedConsumer;
import io.dropwizard.actors.actor.UnmanagedPublisher;
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
      Class<? extends Message> clazz,
      MessageHandlingFunction<Message, Boolean> handlerFunction,
      Function<Throwable, Boolean> errorCheckFunction) {
    this(new UnmanagedPublisher<>(name, config, connection, mapper),
        new UnmanagedConsumer<>(
            name, config, connection, mapper, retryStrategyFactory, clazz, handlerFunction, errorCheckFunction));
  }

  public final void publishWithDelay(Message message, long delayMilliseconds) throws Exception {
    publishActor().publishWithDelay(message, delayMilliseconds);
  }

  public final void publish(Message message) throws Exception {
    publishActor().publish(message);
  }

  public final void publish(Message message, AMQP.BasicProperties properties) throws Exception {
    publishActor().publish(message, properties);
  }

  private UnmanagedPublisher<Message> publishActor() {
    if (isNull(publishActor)) {
      throw new NotImplementedException("PublishActor is not initialized");
    }
    return publishActor;
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
}
