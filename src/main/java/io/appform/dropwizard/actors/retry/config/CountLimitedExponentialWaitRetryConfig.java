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
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * No retry will be done
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CountLimitedExponentialWaitRetryConfig extends RetryConfig {

    @Min(1)
    private int maxAttempts = 1;

    @NotNull
    @Valid
    private Duration maxTimeBetweenRetries = Duration.milliseconds(500);

    @Min(1)
    @Max(Long.MAX_VALUE)
    private long multipier = 1;

    public CountLimitedExponentialWaitRetryConfig() {
        super(RetryType.COUNT_LIMITED_EXPONENTIAL_BACKOFF);
    }

    @Builder
    public CountLimitedExponentialWaitRetryConfig(int maxAttempts,
                                                  Duration maxTimeBetweenRetries,
                                                  long multipier,
                                                  Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.maxTimeBetweenRetries = maxTimeBetweenRetries;
        this.multipier = multipier;
    }
}
