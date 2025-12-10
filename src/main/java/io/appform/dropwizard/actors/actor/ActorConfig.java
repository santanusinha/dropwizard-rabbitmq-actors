/*
 * Copyright (c) 2019 Santanu Sinha <santanu.sinha@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.appform.dropwizard.actors.actor;

import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import io.dropwizard.validation.ValidationMethod;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Configuration for an actor
 */
@Data
@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ActorConfig {

    @NotNull
    @NotEmpty
    private String exchange;

    @Builder.Default
    private boolean delayed = false;

    @Builder.Default
    private boolean priorityQueue = false;

    @Builder.Default
    private int maxPriority = 10;

    @Builder.Default
    private DelayType delayType = DelayType.DELAYED;

    @Builder.Default
    private QueueType queueType = QueueType.CLASSIC;

    @Builder.Default
    private HaMode haMode = HaMode.ALL;

    // https://www.rabbitmq.com/ha.html
    // Would be used only if ha-mode is set to "exactly", use it to set the number of replica nodes
    @Builder.Default
    private String haParams = "";

    // https://www.rabbitmq.com/lazy-queues.html
    @Builder.Default
    private boolean lazyMode = false;

    @Builder.Default
    private int quorumInitialGroupSize = 3;

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

    @Valid
    @NotNull
    @Builder.Default
    private RetryConfig retryConfig = new NoRetryConfig();

    private ExceptionHandlerConfig exceptionHandlerConfig;

    @Valid
    private ProducerConfig producer;

    @Valid
    private ConsumerConfig consumer;

    @Valid
    private TtlConfig ttlConfig;

    @Min(2)
    @Max(32)
    private Integer shardCount;

    @Valid
    private SidelineProcessorConfig sidelineProcessorConfig;

    public boolean isSidelineProcessorEnabled() {
        return Objects.nonNull(sidelineProcessorConfig);
    }

    public boolean isSharded() {
        return Objects.nonNull(shardCount);
    }

    public int getShardCount() {
        return Objects.nonNull(shardCount) ? shardCount : 0;
    }

    @ValidationMethod(message = "Concurrency should be multiple of shard count for sharded queue.")
    public boolean isValidSharding() {
        return !isSharded() || getConcurrency() % getShardCount() == 0;
    }

    @ValidationMethod(message = "SidelineProcessor Concurrency should be multiple of shard count for sharded queue.")
    public boolean isValidShardingSidelineProcessor() {
        return !isSharded() || !isSidelineProcessorEnabled()
                || getSidelineProcessorConfig().getConcurrency() % getShardCount() == 0;
    }

}
