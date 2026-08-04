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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.appform.dropwizard.actors.retry.RetryType;
import io.appform.dropwizard.actors.retry.waitstrategies.JitterType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * Configure a retry strategy
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "COUNT_LIMITED_EXPONENTIAL_BACKOFF", value =
                CountLimitedExponentialWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "COUNT_LIMITED_FIXED_WAIT", value = CountLimitedFixedWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "COUNT_LIMITED_INCREMENTAL_WAIT", value =
                CountLimitedIncrementalWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "NO_RETRY", value = NoRetryConfig.class),
        @JsonSubTypes.Type(name = "TIME_LIMITED_EXPONENTIAL_BACKOFF", value =
                TimeLimitedExponentialWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "TIME_LIMITED_FIXED_WAIT", value = TimeLimitedFixedWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "TIME_LIMITED_INCREMENTAL_WAIT", value = TimeLimitedIncrementalWaitRetryConfig.class)
})
@Data
@EqualsAndHashCode
@ToString
public abstract class RetryConfig {
    private final RetryType type;

    private Set<String> retriableExceptions;

    /**
     * When enabled, a random jitter (bounded by {@link #jitterFactor}) is added on top of the
     * wait computed by the configured wait strategy to spread out retries across clients and
     * avoid a thundering herd.
     */
    private boolean enableJitter;

    /**
     * Fraction (0.0 to 1.0) of the computed wait used as the upper bound for the random jitter.
     * Only effective when {@link #enableJitter} is true.
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double jitterFactor;

    /**
     * Algorithm used to compute jitter. Only effective when {@link #enableJitter} is true.
     */
    @NotNull
    private JitterType jitterType = JitterType.BOUNDED;

    protected RetryConfig(RetryType type) {
        this.type = type;
    }

    protected RetryConfig(RetryType type,
                          Set<String> retriableExceptions) {
        this.type = type;
        this.retriableExceptions = retriableExceptions;
    }
}
