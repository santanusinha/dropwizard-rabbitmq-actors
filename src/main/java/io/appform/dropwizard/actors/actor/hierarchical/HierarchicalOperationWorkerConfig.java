package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.ProducerConfig;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HierarchicalOperationWorkerConfig {

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

}
