package io.dropwizard.actors.actor;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import io.dropwizard.actors.connectivity.RMQConnection;
import io.dropwizard.actors.retry.RetryStrategyFactory;
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

  private final UnmanagedPublishActor<Message> publishActor;
  private final UnmanagedConsumeActor<Message> consumeActor;

  public UnmanagedBaseActor(UnmanagedPublishActor<Message> publishActor,
      UnmanagedConsumeActor<Message> consumeActor) {
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
    this(new UnmanagedPublishActor<>(name, config, connection, mapper),
        new UnmanagedConsumeActor<>(
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

  private UnmanagedPublishActor<Message> publishActor() {
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
