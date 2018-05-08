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
public class CountLimitedIncrementalWaitRetryConfig extends RetryConfig {

    @Min(1)
    private int maxAttempts = 1;

    @NotNull
    @Valid
    private Duration initialWaitTime = Duration.milliseconds(500);

    @NotNull
    @Valid
    private Duration waitIncrement = Duration.milliseconds(250);

    public CountLimitedIncrementalWaitRetryConfig() {
        super(RetryType.COUNT_LIMITED_INCREMENTAL_WAIT);
    }

    @Builder
    public CountLimitedIncrementalWaitRetryConfig(int maxAttempts, Duration initialWaitTime, Duration waitIncrement, Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_INCREMENTAL_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.initialWaitTime = initialWaitTime;
        this.waitIncrement = waitIncrement;
    }
}
