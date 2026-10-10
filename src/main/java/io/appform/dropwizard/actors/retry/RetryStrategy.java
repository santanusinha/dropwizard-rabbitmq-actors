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


import lombok.Getter;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.retry.async.ActorRetryer;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Defines retry policy and delegates execution to the executor bound by the factory.
 */
public abstract class RetryStrategy {

    @Getter
    private final ActorRetryerBuilder.Policy<Boolean> policy;
    private ActorRetryer actorRetryer;

    protected RetryStrategy(ActorRetryerBuilder.Policy<Boolean> policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    final void setActorRetryer(ActorRetryer actorRetryer) {
        Objects.requireNonNull(actorRetryer, "actorRetryer");
        if (this.actorRetryer != null) {
            throw new IllegalStateException("Retry executor has already been bound");
        }
        this.actorRetryer = actorRetryer;
    }

    public boolean execute(Callable<Boolean> callable, String queueName, byte[] body,
                           AMQP.BasicProperties properties) throws Exception {
        return retryer().call(callable, queueName, body, properties);
    }

    public void start() throws Exception {
        retryer().start();
    }

    public void stop() throws Exception {
        retryer().stop();
    }

    private ActorRetryer retryer() {
        if (actorRetryer == null) {
            throw new IllegalStateException("Retry executor must be bound by RetryStrategyFactory");
        }
        return actorRetryer;
    }

}
