package io.appform.dropwizard.actors.retry.async;

import com.rabbitmq.client.AMQP;

import io.appform.dropwizard.actors.retry.WaitMode;
import java.util.concurrent.Callable;

public interface ActorRetryer {

    default void start() throws Exception {
    }

    default void stop() throws Exception {
    }

    boolean call(Callable<Boolean> callable, String queueName, byte[] body, AMQP.BasicProperties properties) throws Exception;

    default WaitMode getRetryMode() {
        return WaitMode.SYNC;
    }

}
