package io.dropwizard.actors.retry.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.actors.retry.RetryType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Configure a retry strategy
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "PAYMENT_WALLET_TO_WALLET", value = CountLimitedExponentialWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "PAYMENT_WALLET_TO_WALLET", value = CountLimitedFixedWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "PAYMENT_WALLET_TO_WALLET", value = CountLimitedIncrementalWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "PAYMENT_WALLET_TO_WALLET", value = NoRetryConfig.class),
        @JsonSubTypes.Type(name = "PAYMENT_WALLET_TO_WALLET", value = TimeLimitedExponentialWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "PAYMENT_WALLET_TO_WALLET", value = TimeLimitedFixedWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "PAYMENT_WALLET_TO_WALLET", value = TimeLimitedIncrementalWaitRetryConfig.class)
})
@Data
@EqualsAndHashCode
@ToString
public abstract class RetryConfig {
    private final RetryType type;

    protected RetryConfig(RetryType type) {
        this.type = type;
    }
}
