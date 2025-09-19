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

package io.appform.dropwizard.actors.retry.config;

import io.appform.dropwizard.actors.retry.RetryType;
import io.dropwizard.util.Duration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Set;

/**
 * No retry will be done
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CountLimitedIncrementalWaitRetryConfig extends RetryConfig {

    @Min(1)
    private int maxAttempts = 1;

    @NotNull
    @Valid
    private Duration initialWaitTime = Duration.milliseconds(500);

    @NotNull
    @Valid
    private Duration waitIncrement = Duration.milliseconds(250);

    public CountLimitedIncrementalWaitRetryConfig() {
        super(RetryType.COUNT_LIMITED_INCREMENTAL_WAIT);
    }

    @Builder
    public CountLimitedIncrementalWaitRetryConfig(int maxAttempts,
                                                  Duration initialWaitTime,
                                                  Duration waitIncrement,
                                                  Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_INCREMENTAL_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.initialWaitTime = initialWaitTime;
        this.waitIncrement = waitIncrement;
    }
}
