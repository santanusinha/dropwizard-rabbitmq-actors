package io.appform.dropwizard.actors.retry.async;

import com.github.rholder.retry.RetryerBuilder;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

@Slf4j
@AllArgsConstructor
public class AsyncRetryer implements ActorRetryer {

    private final RetryConfig retryConfig;
    private final RetryerBuilder<Boolean> retryerBuilder;
    private final AsyncRetryScheduler asyncRetryScheduler;

    @Override
    public <T> boolean call(Callable<Boolean> callable, T body, AMQP.BasicProperties properties) throws Exception {
        try {
            var result = callable.call();
            if(Boolean.TRUE.equals(result)) return true;
        } catch (Exception e) {
            log.error("Error while attempting call...", e);
        }
        asyncRetryScheduler.schedule(body, properties);
        return true;
    }

}
