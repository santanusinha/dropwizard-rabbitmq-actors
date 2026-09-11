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

package io.appform.dropwizard.actors.retry;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;

import java.util.concurrent.Callable;

/**
 * Base for all retry strategies
 */
public abstract class RetryStrategy {
    private final RetryPolicy<Boolean> retryPolicy;

    protected RetryStrategy(RetryPolicy<Boolean> retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public boolean execute(Callable<Boolean> callable) throws Exception {
        return Failsafe.with(retryPolicy).get(() -> callable.call());
    }
}
