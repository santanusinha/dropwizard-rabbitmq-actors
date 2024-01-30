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
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.connectivity.strategy.SharedConnectionStrategy;
import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import io.dropwizard.validation.ValidationMethod;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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

    public boolean isSharded() {
        return Objects.nonNull(shardCount);
    }

    @ValidationMethod(message = "Concurrency should be multiple of shard count for sharded queue.")
    public boolean isValidSharding() {
        return !isSharded() || getConcurrency() % getShardCount() == 0;
    }

    @ValidationMethod(message = "Custom connection names should be different from default connection names")
    public boolean isCustomConnectionNamesValid() {

        if (Objects.isNull(producer) && Objects.isNull(consumer)) {
            return true;
        }

        AtomicBoolean validConnectionNames = new AtomicBoolean(true);

        getConnectionNames().forEach(connectionName -> {
            if (Constants.DEFAULT_CONNECTIONS.contains(connectionName)) {
                validConnectionNames.set(false);
            }
        });

        return validConnectionNames.get();
    }

    private Set<String> getConnectionNames() {
        Set<String> proposedConnectionNames = new HashSet<>();
        if (Objects.nonNull(producer) && Objects.nonNull(producer.getConnectionIsolationStrategy()) &&
                producer.getConnectionIsolationStrategy() instanceof SharedConnectionStrategy) {
            proposedConnectionNames.add(((SharedConnectionStrategy) producer.getConnectionIsolationStrategy()).getName());
        }
        if (Objects.nonNull(consumer) && Objects.nonNull(consumer.getConnectionIsolationStrategy()) &&
                consumer.getConnectionIsolationStrategy() instanceof SharedConnectionStrategy) {
            proposedConnectionNames.add(((SharedConnectionStrategy) consumer.getConnectionIsolationStrategy()).getName());
        }

        return proposedConnectionNames;
    }

}
