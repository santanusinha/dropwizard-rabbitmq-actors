package io.appform.dropwizard.actors.messaging.config;

import io.appform.dropwizard.actors.actor.ProducerConfig;
import io.appform.dropwizard.actors.messaging.ExchangeType;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An exchange and the queues bound to it. Every exchange must have at least one queue, consumed by this service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeSpec {

    /**
     * How the exchange routes messages to its queues: DIRECT (exact routing key), TOPIC (routing key pattern) or
     * FANOUT (every queue). Can't be changed once the exchange exists.
     */
    @NotNull
    private ExchangeType type;

    /**
     * Declare the exchange as an {@code x-delayed-message} exchange (rabbitmq_delayed_message_exchange plugin) so
     * messages can be published with a delay.
     *
     * <p><b>Risk:</b> the plugin does not support the {@code mandatory} flag, so the publisher can't detect a message
     * that no binding matches, even with publisher confirms. Such a message is dropped silently.
     */
    @Builder.Default
    private boolean delayed = false;

    /**
     * Wait for the broker's publisher confirm on every publish (default). A publish then fails if the broker did not
     * accept it, or with {@code UnroutableMessageException} if no binding matched (non-delayed exchanges).
     *
     * <p>Reduces publish throughput: each publisher waits for the broker's confirm before its next publish. Disable for
     * fire-and-forget throughput, at the price of unroutable messages being only logged, or use several publishers for
     * the exchange.
     */
    @Builder.Default
    private boolean publisherConfirms = true;

    @Min(100)
    @Max(60_000)
    @Builder.Default
    private long confirmTimeoutMs = 5_000;

    /**
     * Producer connection isolation.
     */
    @Valid
    private ProducerConfig producer;

    /**
     * Queues bound to this exchange, keyed by logical queue name.
     */
    @NotNull
    @Valid
    @Builder.Default
    private Map<String, QueueSpec> queues = new LinkedHashMap<>();
}
