package io.dropwizard.actors.actor;

import io.dropwizard.actors.retry.config.NoRetryConfig;
import io.dropwizard.actors.retry.config.RetryConfig;
import lombok.*;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Configuration for an actor
 */
@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActorConfig {
    @NotNull
    @NotEmpty
    private String exchange;

    private boolean delayed = false;

    private DelayType delayType = DelayType.DELAYED;

    @NotNull
    @NotEmpty
    private String prefix = "actors";

    @Min(1)
    @Max(100)
    private int concurrency = 3;

    @Min(1)
    @Max(100)
    private int prefetchCount = 1;

    @NotNull
    @Valid
    private RetryConfig retryConfig = new NoRetryConfig();

}
