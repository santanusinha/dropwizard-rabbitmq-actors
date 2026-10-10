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

import io.appform.dropwizard.actors.retry.config.CountLimitedExponentialWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.CountLimitedIncrementalWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import io.appform.dropwizard.actors.retry.config.TimeLimitedExponentialWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.TimeLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.TimeLimitedIncrementalWaitRetryConfig;
import io.appform.dropwizard.actors.retry.impl.CountLimitedExponentialWaitRetryStrategy;
import io.appform.dropwizard.actors.retry.impl.CountLimitedFixedWaitRetryStrategy;
import io.appform.dropwizard.actors.retry.impl.CountLimitedIncrementalWaitRetryStrategy;
import io.appform.dropwizard.actors.retry.impl.NoRetryStrategy;
import io.appform.dropwizard.actors.retry.impl.TimeLimitedExponentialWaitRetryStrategy;
import io.appform.dropwizard.actors.retry.impl.TimeLimitedFixedWaitRetryStrategy;
import io.appform.dropwizard.actors.retry.impl.TimeLimitedIncrementalWaitRetryStrategy;
import io.appform.dropwizard.actors.retry.async.AsyncRetryScheduler;
import io.appform.dropwizard.actors.retry.async.ActorRetryer;
import io.appform.dropwizard.actors.retry.async.AsyncRetryer;
import io.appform.dropwizard.actors.retry.async.TtlRequeueRetryScheduler;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Creates strategy based on config
 */
public class RetryStrategyFactory {

    public RetryStrategy create(RetryConfig config) {
        return create(config, null);
    }

    public RetryStrategy create(RetryConfig config, AsyncRetryScheduler scheduler) {
        Objects.requireNonNull(config, "config");
        var strategy = createStrategy(config);
        strategy.setActorRetryer(createActorRetryer(config, strategy.getPolicy(), scheduler));
        return strategy;
    }

    public RetryStrategy create(RetryConfig config, ActorConfig actorConfig, RMQConnection connection,
                                String exchange, String queueName) {
        Objects.requireNonNull(config, "config");
        var mode = Objects.requireNonNull(config.getRetryMode(), "retryMode");
        var scheduler = mode == RetryMode.ASYNC
                ? createScheduler(config, actorConfig, connection, exchange, queueName) : null;
        return create(config, scheduler);
    }

    protected AsyncRetryScheduler createScheduler(RetryConfig config, ActorConfig actorConfig,
                                                   RMQConnection connection, String exchange, String queueName) {
        Set<String> queues = actorConfig.isSharded()
                ? IntStream.range(0, actorConfig.getShardCount())
                        .mapToObj(shard -> NamingUtils.getShardedQueueName(queueName, shard))
                        .collect(Collectors.toSet())
                : Set.of(queueName);
        return new TtlRequeueRetryScheduler(
                new TtlRequeueRetryScheduler.Context(connection, actorConfig, exchange, queueName, queues), 5000);
    }

    protected ActorRetryer createActorRetryer(RetryConfig config,
                                              ActorRetryerBuilder.Policy<Boolean> policy,
                                              AsyncRetryScheduler scheduler) {
        var mode = Objects.requireNonNull(config.getRetryMode(), "retryMode");
        if (mode == RetryMode.ASYNC) {
            return new AsyncRetryer(policy, Objects.requireNonNull(scheduler, "Async retry scheduler is required"));
        }
        var retryer = policy.buildSyncRetryer();
        return (callable, queueName, body, properties) -> retryer.call(callable);
    }

    protected RetryStrategy createStrategy(RetryConfig config) {
        return switch (config.getType()) {
            case NO_RETRY -> new NoRetryStrategy((NoRetryConfig) config);
            case TIME_LIMITED_EXPONENTIAL_BACKOFF ->
                    new TimeLimitedExponentialWaitRetryStrategy((TimeLimitedExponentialWaitRetryConfig) config);
            case TIME_LIMITED_INCREMENTAL_WAIT ->
                    new TimeLimitedIncrementalWaitRetryStrategy((TimeLimitedIncrementalWaitRetryConfig) config);
            case TIME_LIMITED_FIXED_WAIT ->
                    new TimeLimitedFixedWaitRetryStrategy((TimeLimitedFixedWaitRetryConfig) config);
            case COUNT_LIMITED_EXPONENTIAL_BACKOFF ->
                    new CountLimitedExponentialWaitRetryStrategy((CountLimitedExponentialWaitRetryConfig) config);
            case COUNT_LIMITED_INCREMENTAL_WAIT ->
                    new CountLimitedIncrementalWaitRetryStrategy((CountLimitedIncrementalWaitRetryConfig) config);
            case COUNT_LIMITED_FIXED_WAIT ->
                    new CountLimitedFixedWaitRetryStrategy((CountLimitedFixedWaitRetryConfig) config);
        };
    }
}
