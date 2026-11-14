package io.appform.dropwizard.actors.messaging.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.QueueType;
import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.dropwizard.actors.retry.config.NoRetryConfig;
import io.appform.dropwizard.actors.retry.config.RetryConfig;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A consumer queue bound to an exchange.
 *
 * <p>The queue-argument fields ({@link #queueType}, {@link #lazyMode}, {@link #quorumInitialGroupSize},
 * {@link #ttlConfig}) mirror the ones on {@code ActorConfig}, and are mapped back to it in
 * {@link #toQueueArgumentsConfig}, so queue arguments are built by the same code as for actors. Queue arguments can't
 * be changed once the queue exists.
 *
 * <p>Not supported:
 * <ul>
 *   <li>priority queues, since publishers can't set a message priority;</li>
 *   <li>{@code ActorConfig}'s {@code haMode}/{@code haParams}: as queue arguments they don't enable mirroring
 *       (RabbitMQ only mirrors classic queues through policies). For replicated queues use {@link QueueType#QUORUM},
 *       or a mirroring policy matching the queue names.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueSpec {

    /**
     * Binding keys used to bind this queue to its exchange. Required (non-empty) for DIRECT and TOPIC exchanges,
     * must be empty for FANOUT. Immutable once the queue exists (enforced when the Management API is enabled):
     * to change them, use a new queue.
     */
    @NotNull
    @Builder.Default
    private List<String> bindingKeys = new ArrayList<>();

    /**
     * Number of consumers (channels) reading this queue in parallel.
     */
    @Min(1)
    @Max(100)
    @Builder.Default
    private int concurrency = 3;

    /**
     * Maximum unacknowledged messages delivered to each consumer at a time.
     */
    @Min(1)
    @Max(100)
    @Builder.Default
    private int prefetchCount = 1;

    /**
     * In-process retries when the handler throws, before the message is rejected to the sideline queue.
     */
    @Valid
    @NotNull
    @Builder.Default
    private RetryConfig retryConfig = new NoRetryConfig();

    /**
     * What to do once retries are exhausted: reject to the sideline queue (default) or ack and drop.
     */
    private ExceptionHandlerConfig exceptionHandlerConfig;

    /**
     * Consumer connection isolation and consumer tag prefix.
     */
    @Valid
    private ConsumerConfig consumer;

    /**
     * Enables {@code MessageContext.retryLater}: a private delayed exchange bound only to this queue, so a deferred
     * retry is redelivered to this queue alone and never re-broadcast to the other queues of the exchange.
     * Requires the rabbitmq_delayed_message_exchange plugin.
     */
    @Builder.Default
    private boolean retryLaterEnabled = false;

    /**
     * Queue type: CLASSIC, CLASSIC_V2 or QUORUM ({@code x-queue-type}).
     */
    @Builder.Default
    private QueueType queueType = QueueType.CLASSIC;

    /**
     * Classic queues only: {@code x-queue-mode: lazy}, keeping messages on disk. Ignored by RabbitMQ 3.12+, where
     * classic queues always behave this way.
     */
    @Builder.Default
    private boolean lazyMode = false;

    /**
     * Quorum queues only: number of replicas the queue is created with ({@code x-quorum-initial-group-size}).
     */
    @Builder.Default
    private int quorumInitialGroupSize = 3;

    /**
     * Queue auto-delete: the queue (and its sideline queue) is deleted after being unused for this long
     * ({@code x-expires}). This is not a message TTL.
     */
    @Valid
    private TtlConfig ttlConfig;

    /**
     * Maps this spec's queue-argument fields to an {@code ActorConfig}, so queue arguments are built by the same code
     * as for actors.
     *
     * @param exchange broker exchange name (required by {@code ActorConfig}, not used for queue arguments)
     */
    @JsonIgnore
    public ActorConfig toQueueArgumentsConfig(final String exchange) {
        return ActorConfig.builder()
                .exchange(exchange)
                .queueType(queueType)
                .lazyMode(lazyMode)
                .quorumInitialGroupSize(quorumInitialGroupSize)
                .ttlConfig(ttlConfig)
                .build();
    }
}
