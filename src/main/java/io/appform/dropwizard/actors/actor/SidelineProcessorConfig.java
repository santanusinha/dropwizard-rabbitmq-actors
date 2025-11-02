package io.appform.dropwizard.actors.actor;

import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SidelineProcessorConfig {

    @Min(1)
    @Max(100)
    @Builder.Default
    private int concurrency = 1;

    @NotNull
    @Valid
    @Builder.Default
    private RetryConfig retryConfig = new NoRetryConfig();


    @Valid
    private ConsumerConfig consumerConfig;

}
