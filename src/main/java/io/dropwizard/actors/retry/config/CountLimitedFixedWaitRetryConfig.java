package io.dropwizard.actors.retry.config;

import io.dropwizard.actors.retry.RetryType;
import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * No retry will be done
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CountLimitedFixedWaitRetryConfig extends RetryConfig {

    @Min(1)
    private int maxAttempts = 1;

    @NotNull
    @Valid
    private Duration waitTime = Duration.milliseconds(500);

    public CountLimitedFixedWaitRetryConfig() {
        super(RetryType.COUNT_LIMITED_FIXED_WAIT);
    }

    @Builder
    public CountLimitedFixedWaitRetryConfig(int maxAttempts, Duration waitTime, Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.waitTime = waitTime;
    }
}
