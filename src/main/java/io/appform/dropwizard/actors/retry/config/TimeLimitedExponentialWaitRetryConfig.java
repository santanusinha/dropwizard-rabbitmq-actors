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
import jakarta.validation.constraints.Max;
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
public class TimeLimitedExponentialWaitRetryConfig extends RetryConfig {

    @NotNull
    @Valid
    private Duration maxTime = Duration.seconds(30);

    @NotNull
    @Valid
    private Duration maxTimeBetweenRetries = Duration.milliseconds(500);

    @Min(1)
    @Max(Long.MAX_VALUE)
    private long multipier = 1;

    public TimeLimitedExponentialWaitRetryConfig() {
        super(RetryType.TIME_LIMITED_EXPONENTIAL_BACKOFF);
    }

    @Builder
    public TimeLimitedExponentialWaitRetryConfig(final Duration maxTime,
                                                 final Duration maxTimeBetweenRetries,
                                                 final long multipier,
                                                 final Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxTime = maxTime;
        this.maxTimeBetweenRetries = maxTimeBetweenRetries;
        this.multipier = multipier;
    }
}
