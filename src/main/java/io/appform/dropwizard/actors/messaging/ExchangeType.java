package io.appform.dropwizard.actors.messaging;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AMQP exchange types supported by the exchange-centric messaging API.
 */
@Getter
@RequiredArgsConstructor
public enum ExchangeType {
    /**
     * Routes a message to every queue bound with a binding key equal to the message's routing key.
     */
    DIRECT("direct"),
    /**
     * Routes a message to every queue whose binding pattern ({@code *} = exactly one word, {@code #} = zero or
     * more words) matches the message's routing key.
     */
    TOPIC("topic"),
    /**
     * Broadcasts a message to every bound queue; the routing key is ignored.
     */
    FANOUT("fanout");

    private final String amqpType;

    public boolean usesRoutingKey() {
        return this != FANOUT;
    }
}
