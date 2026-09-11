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
 * limitations under the License.
 */

package io.appform.dropwizard.actors.retry.impl;

import dev.failsafe.RetryPolicy;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import io.appform.dropwizard.actors.retry.config.TimeLimitedExponentialWaitRetryConfig;
import io.appform.dropwizard.actors.utils.CommonUtils;

import java.time.Duration;

/**
 * Limits retry time
 */
public class TimeLimitedExponentialWaitRetryStrategy extends RetryStrategy {
    public TimeLimitedExponentialWaitRetryStrategy(TimeLimitedExponentialWaitRetryConfig config) {
        super(RetryPolicy.<Boolean>builder()
                .handleIf(exception -> CommonUtils.isRetriable(config.getRetriableExceptions(), exception))
                .withMaxDuration(Duration.ofMillis(config.getMaxTime().toMilliseconds()))
                .withMaxRetries(-1)
                .withBackoff(
                        Duration.ofMillis(2 * config.getMultipier()),
                        Duration.ofMillis(config.getMaxTimeBetweenRetries().toMilliseconds()),
                        2.0)
                .build());
    }
}
