package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.*;
import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HierarchicalWorkerActorConfig {

    @Min(1)
    @Max(100)
    @Builder.Default
    private int concurrency = 3;

    @Min(1)
    @Max(100)
    @Builder.Default
    private int prefetchCount = 1;

    @Min(2)
    @Max(32)
    private Integer shardCount;

    @Valid
    private ProducerConfig producer;

    @Valid
    private ConsumerConfig consumer;

    @Builder.Default
    private boolean delayed = false;

    @Builder.Default
    private DelayType delayType = DelayType.DELAYED;

    @Builder.Default
    private boolean priorityQueue = false;

    @Builder.Default
    private QueueType queueType = QueueType.CLASSIC;

    @Builder.Default
    private int quorumInitialGroupSize = 3;

    @Builder.Default
    private HaMode haMode = HaMode.ALL;

    @Builder.Default
    private String haParams = "";

    @Builder.Default
    private int maxPriority = 10;

    @Builder.Default
    private boolean lazyMode = false;

    @NotNull
    @Valid
    @Builder.Default
    private RetryConfig retryConfig = new NoRetryConfig();

    private ExceptionHandlerConfig exceptionHandlerConfig;

    @Valid
    private TtlConfig ttlConfig;

}
