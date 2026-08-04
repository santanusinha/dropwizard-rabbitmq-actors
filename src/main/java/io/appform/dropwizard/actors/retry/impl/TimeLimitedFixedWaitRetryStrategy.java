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

package io.appform.dropwizard.actors.retry.impl;

import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import io.appform.dropwizard.actors.retry.RetryStrategy;
import io.appform.dropwizard.actors.retry.config.TimeLimitedFixedWaitRetryConfig;

import java.util.concurrent.TimeUnit;

/**
 * Limits retry time
 */
public class TimeLimitedFixedWaitRetryStrategy extends RetryStrategy {
    public TimeLimitedFixedWaitRetryStrategy(TimeLimitedFixedWaitRetryConfig config) {
        super(config,
                StopStrategies.stopAfterDelay(config.getMaxTime().toMilliseconds(), TimeUnit.MILLISECONDS),
                WaitStrategies.fixedWait(config.getWaitTime().toMilliseconds(), TimeUnit.MILLISECONDS));
    }
}
