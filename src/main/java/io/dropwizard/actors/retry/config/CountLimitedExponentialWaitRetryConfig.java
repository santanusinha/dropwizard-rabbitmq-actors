package io.dropwizard.actors.retry.config;

import io.dropwizard.actors.retry.RetryType;
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
    public CountLimitedExponentialWaitRetryConfig(int maxAttempts, Duration maxTimeBetweenRetries, long multipier, Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.maxTimeBetweenRetries = maxTimeBetweenRetries;
        this.multipier = multipier;
    }
}
