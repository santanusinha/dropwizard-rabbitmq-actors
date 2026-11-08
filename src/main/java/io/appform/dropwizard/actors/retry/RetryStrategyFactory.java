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

import com.github.rholder.retry.Retryer;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.connectivity.PublishingChannel;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.retry.async.ActorRetryer;
import io.appform.dropwizard.actors.retry.async.AsyncRetryer;
import io.appform.dropwizard.actors.retry.async.TtlRequeueRetryScheduler;
import io.appform.dropwizard.actors.retry.config.CountLimitedExponentialWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.retry.config.CountLimitedIncrementalWaitRetryConfig;
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
import java.util.Objects;

/**
 * Creates strategy based on config
 */
public class RetryStrategyFactory {

    public static final long DEFAULT_CONFIRM_TIMEOUT_MS = 500;

    /**
     * @deprecated
     * Use the other {@link RetryStrategyFactory::create}
     */
    @Deprecated(forRemoval = true)
    public RetryStrategy create(RetryConfig config) {
        return createStrategy(config);
    }

    public RetryStrategy create(ActorConfig actorConfig, RMQConnection connection, String queueName) {
        return create(actorConfig.getRetryConfig(), actorConfig, connection, actorConfig.getExchange(), queueName);
    }

    public RetryStrategy create(
        RetryConfig retryConfig, ActorConfig actorConfig, RMQConnection connection, String exchange, String queueName) {
        var retryMode = retryConfig.getWaitMode();
        var strategy = createStrategy(retryConfig);
        if (retryMode == null || retryMode == WaitMode.SYNC) {
            return strategy;
        }
        var retryPolicy = Objects.requireNonNull(strategy.getRetryPolicy(), "retryPolicy");
        var ttlRequeue = retryConfig.getTtlRequeue();
        var confirmTimeoutMs = ttlRequeue == null || ttlRequeue.getConfirmTimeout() == null
                ? DEFAULT_CONFIRM_TIMEOUT_MS
                : ttlRequeue.getConfirmTimeout().toMilliseconds();
        var asyncRetryer = new AsyncRetryer(retryPolicy,
            new TtlRequeueRetryScheduler(connection, retryPolicy, actorConfig, exchange, queueName, ttlRequeue,
                    new PublishingChannel(connection, confirmTimeoutMs))
        );
        return strategy.setActorRetryer(asyncRetryer);
    }

    protected RetryStrategy createStrategy(RetryConfig config) {
        return switch (config.getType()) {
            case NO_RETRY -> new NoRetryStrategy();
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

    public static ActorRetryer syncRetryer(Retryer<Boolean> retryer) {
        return (callable, queueName, body, properties) -> retryer.call(callable);
    }

}
