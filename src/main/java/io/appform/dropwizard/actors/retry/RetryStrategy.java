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

package io.appform.dropwizard.actors.retry;

import com.github.rholder.retry.RetryerBuilder;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.retry.async.ActorRetryer;
import io.appform.dropwizard.actors.retry.async.AsyncRetryer;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import lombok.AllArgsConstructor;

import java.util.concurrent.Callable;

/**
 * Base for all retry strategies
 */
@AllArgsConstructor
public abstract class RetryStrategy {

    private final ActorRetryer actorRetryer;

    protected RetryStrategy(RetryerBuilder<Boolean> retryerBuilder, RetryConfig retryConfig) {
        if(RetryMode.SYNC.equals(retryConfig.getRetryMode())) {
            this.actorRetryer = syncRetryer(retryerBuilder);
        } else {
            this.actorRetryer = new AsyncRetryer(retryConfig, retryerBuilder, null);
        }
    }

    public <T> boolean execute(Callable<Boolean> callable, T body, AMQP.BasicProperties properties) throws Exception {
        return actorRetryer.call(callable, body, properties);
    }

    public ActorRetryer syncRetryer(RetryerBuilder<Boolean> retryerBuilder) {
        var retryer = retryerBuilder.build();
        return new ActorRetryer() {
            @Override
            public <T> boolean call(Callable<Boolean> callable, T body, AMQP.BasicProperties properties) throws Exception {
                return retryer.call(callable);
            }
        };
    }

}
