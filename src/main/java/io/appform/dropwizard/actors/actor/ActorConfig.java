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
import io.appform.dropwizard.actors.actor.metadata.generators.MessageDelayMetaGenerator;
import io.appform.dropwizard.actors.actor.metadata.generators.MessageExpiredMetaGenerator;
import io.appform.dropwizard.actors.actor.metadata.generators.MessageReDeliveredMetaGenerator;
import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import io.dropwizard.validation.ValidationMethod;
import java.util.List;
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
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActorConfig {

    public static final List<String> DEFAULT_METADATA_GENERATORS = List.of(
            MessageReDeliveredMetaGenerator.class.getName(),
            MessageDelayMetaGenerator.class.getName(),
            MessageExpiredMetaGenerator.class.getName()
    );

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

    private List<String> messageMetaGeneratorClasses;

    public boolean isSharded() {
        return Objects.nonNull(shardCount);
    }

    @ValidationMethod(message = "Concurrency should be multiple of shard count for sharded queue.")
    public boolean isValidSharding() {
        return !isSharded() || getConcurrency() % getShardCount() == 0;
    }

}
