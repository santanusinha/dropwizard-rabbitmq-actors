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
public class TimeLimitedIncrementalWaitRetryConfig extends RetryConfig {

    @NotNull
    @Valid
    private Duration maxTime = Duration.seconds(30);

    @NotNull
    @Valid
    private Duration initialWaitTime = Duration.milliseconds(500);

    @NotNull
    @Valid
    private Duration waitIncrement = Duration.milliseconds(250);

    public TimeLimitedIncrementalWaitRetryConfig() {
        super(RetryType.TIME_LIMITED_INCREMENTAL_WAIT);
    }

    @Builder
    public TimeLimitedIncrementalWaitRetryConfig(Duration maxTime, Duration initialWaitTime, Duration waitIncrement, Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_INCREMENTAL_WAIT, retriableExceptions);
        this.maxTime = maxTime;
        this.initialWaitTime = initialWaitTime;
        this.waitIncrement = waitIncrement;
    }
}
