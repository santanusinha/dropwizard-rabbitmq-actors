package io.dropwizard.actors.retry.config;

import io.dropwizard.actors.retry.RetryType;
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
public class TimeLimitedFixedWaitRetryConfig extends RetryConfig {

    @NotNull
    @Valid
    private Duration maxTime = Duration.seconds(30);

    @NotNull
    @Valid
    private Duration waitTime = Duration.milliseconds(500);

    public TimeLimitedFixedWaitRetryConfig() {
        super(RetryType.TIME_LIMITED_FIXED_WAIT);
    }

    @Builder
    public TimeLimitedFixedWaitRetryConfig(Duration maxTime, Duration waitTime, Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxTime = maxTime;
        this.waitTime = waitTime;
    }
}
