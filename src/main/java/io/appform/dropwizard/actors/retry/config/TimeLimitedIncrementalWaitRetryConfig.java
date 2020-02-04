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
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * No retry will be done
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedIncrementalWaitRetryConfig extends RetryConfig {

    @NotNull
    @Valid
    private Duration maxTime = Duration.seconds(30);

    @NotNull
    @Valid
    private Duration initialWaitTime = Duration.milliseconds(500);

    @NotNull
    @Valid
    private Duration waitIncrement = Duration.milliseconds(250);

    public TimeLimitedIncrementalWaitRetryConfig() {
        super(RetryType.TIME_LIMITED_INCREMENTAL_WAIT);
    }

    @Builder
    public TimeLimitedIncrementalWaitRetryConfig(Duration maxTime, Duration initialWaitTime,
                                                 Duration waitIncrement, Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_INCREMENTAL_WAIT, retriableExceptions);
        this.maxTime = maxTime;
        this.initialWaitTime = initialWaitTime;
        this.waitIncrement = waitIncrement;
    }
}
