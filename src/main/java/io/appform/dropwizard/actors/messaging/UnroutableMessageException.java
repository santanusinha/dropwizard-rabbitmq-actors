package io.appform.dropwizard.actors.messaging;

import io.appform.dropwizard.actors.common.ErrorCode;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import lombok.Getter;

/**
 * Thrown when the broker returned a published message because no queue binding matched its routing key.
 */
@Getter
public class UnroutableMessageException extends RabbitmqActorException {

    /**
     * Logical exchange name (as used in code).
     */
    private final String exchange;
    private final String brokerExchange;
    private final String routingKey;

    public UnroutableMessageException(final String exchange,
                                      final String brokerExchange,
                                      final String routingKey,
                                      final String replyText) {
        super(ErrorCode.UNROUTABLE_MESSAGE, String.format(
                "Message published to exchange '%s' (broker '%s') with routing key '%s' was not routed to any queue "
                        + "(%s)", exchange, brokerExchange, routingKey, replyText), null);
        this.exchange = exchange;
        this.brokerExchange = brokerExchange;
        this.routingKey = routingKey;
    }

    /**
     * The parent's Lombok-generated toString omits the message; stack traces start with toString().
     */
    @Override
    public String toString() {
        return getClass().getName() + ": " + getMessage();
    }
}
