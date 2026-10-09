package io.appform.dropwizard.actors.retry.async;

import com.rabbitmq.client.AMQP;

import java.util.concurrent.Callable;

public interface ActorRetryer {

    <T> boolean call(Callable<Boolean> callable, T body, AMQP.BasicProperties properties) throws Exception;

}
