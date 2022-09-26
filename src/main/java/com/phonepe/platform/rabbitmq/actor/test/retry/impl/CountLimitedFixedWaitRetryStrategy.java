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

package com.phonepe.platform.rabbitmq.actor.test.retry.impl;

import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.phonepe.platform.rabbitmq.actor.test.retry.RetryStrategy;
import com.phonepe.platform.rabbitmq.actor.test.retry.config.CountLimitedFixedWaitRetryConfig;
import com.phonepe.platform.rabbitmq.actor.test.utils.CommonUtils;

import java.util.concurrent.TimeUnit;

/**
 * Limits retry time
 */
public class CountLimitedFixedWaitRetryStrategy extends RetryStrategy {
    public CountLimitedFixedWaitRetryStrategy(CountLimitedFixedWaitRetryConfig config) {
        super(RetryerBuilder.<Boolean>newBuilder()
                .retryIfException(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(),
                        exception))
                .withStopStrategy(StopStrategies.stopAfterAttempt(config.getMaxAttempts()))
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                .withWaitStrategy(
                        WaitStrategies.fixedWait(config.getWaitTime().toMilliseconds(), TimeUnit.MILLISECONDS))
                .build());
    }
}
