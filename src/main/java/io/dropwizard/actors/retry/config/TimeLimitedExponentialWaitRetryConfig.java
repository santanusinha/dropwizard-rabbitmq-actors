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
    public TimeLimitedExponentialWaitRetryConfig(Duration maxTime, Duration maxTimeBetweenRetries, long multipier, Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxTime = maxTime;
        this.maxTimeBetweenRetries = maxTimeBetweenRetries;
        this.multipier = multipier;
    }
}
