package io.dropwizard.actors.retry.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.actors.retry.RetryType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Set;

/**
 * Configure a retry strategy
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "COUNT_LIMITED_EXPONENTIAL_BACKOFF", value = CountLimitedExponentialWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "COUNT_LIMITED_FIXED_WAIT", value = CountLimitedFixedWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "COUNT_LIMITED_INCREMENTAL_WAIT", value = CountLimitedIncrementalWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "NO_RETRY", value = NoRetryConfig.class),
        @JsonSubTypes.Type(name = "TIME_LIMITED_EXPONENTIAL_BACKOFF", value = TimeLimitedExponentialWaitRetryConfig.class),
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
