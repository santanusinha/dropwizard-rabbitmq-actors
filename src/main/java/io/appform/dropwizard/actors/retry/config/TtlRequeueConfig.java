package io.appform.dropwizard.actors.retry.config;

import io.appform.dropwizard.actors.retry.async.TtlRequeueRetryScheduler.IsolationScope;
import io.dropwizard.util.Duration;
import javax.validation.constraints.Min;
import lombok.Data;

@Data
public class TtlRequeueConfig {

    @Min(1)
    private Integer maxRetryQueues = 10;

    @Min(1)
    private Integer maxPlannedAttempts = 10;

    private Duration confirmTimeout = Duration.milliseconds(500);

    private IsolationScope isolationScope = IsolationScope.EXCHANGE;

}
