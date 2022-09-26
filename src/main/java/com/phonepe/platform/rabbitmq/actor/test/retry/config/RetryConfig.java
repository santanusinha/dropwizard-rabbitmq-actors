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

package com.phonepe.platform.rabbitmq.actor.test.retry.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.platform.rabbitmq.actor.test.retry.RetryType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

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

    protected RetryConfig(RetryType type) {
        this.type = type;
    }

    protected RetryConfig(RetryType type,
                          Set<String> retriableExceptions) {
        this.type = type;
        this.retriableExceptions = retriableExceptions;
    }
}
