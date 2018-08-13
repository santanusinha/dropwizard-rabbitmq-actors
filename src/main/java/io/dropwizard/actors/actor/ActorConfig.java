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

    @Builder.Default
    private boolean delayed = false;

    @Builder.Default
    private DelayType delayType = DelayType.DELAYED;

    @NotNull
    @NotEmpty
    @Builder.Default
    private String prefix = "rabbitmq.actors";

    @Min(1)
    @Max(100)
    @Builder.Default
    private int concurrency = 3;

    @Min(1)
    @Max(100)
    @Builder.Default
    private int prefetchCount = 1;

    @NotNull
    @Valid
    @Builder.Default
    private RetryConfig retryConfig = new NoRetryConfig();

}
