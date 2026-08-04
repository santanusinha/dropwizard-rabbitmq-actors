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

import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategy;
import com.github.rholder.retry.WaitStrategy;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import io.appform.dropwizard.actors.retry.waitstrategies.WaitStrategyFactory;
import io.appform.dropwizard.actors.utils.CommonUtils;

import java.util.concurrent.Callable;

/**
 * Baqse for all retry strategies
 */
public abstract class RetryStrategy {
    private final Retryer<Boolean> retryer;

    /**
     * Builds a retryer from the supplied stop and wait strategies.
     * @param config       the retry configuration
     * @param stopStrategy strategy that decides when to stop retrying
     * @param waitStrategy the base wait strategy between retries
     */
    protected RetryStrategy(final RetryConfig config,
                            final StopStrategy stopStrategy,
                            final WaitStrategy waitStrategy) {
        this.retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfException(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(), exception))
                .withStopStrategy(stopStrategy)
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                .withWaitStrategy(getWaitStrategy(config, waitStrategy))
                .build();
    }

    protected RetryStrategy(final Retryer<Boolean> retryer) {
        this.retryer = retryer;
    }

    public boolean execute(Callable<Boolean> callable) throws Exception {
        return retryer.call(callable);
    }

    /**
     * Decides which wait strategy to use for the given config. When jitter is enabled the supplied
     * wait strategy is decorated with a bounded random jitter, otherwise it is used as-is.
     */
    private static WaitStrategy getWaitStrategy(final RetryConfig config, final WaitStrategy waitStrategy) {
        return config.isEnableJitter()
                ? WaitStrategyFactory.withJitter(waitStrategy, config.getJitterFactor())
                : waitStrategy;
    }
}
