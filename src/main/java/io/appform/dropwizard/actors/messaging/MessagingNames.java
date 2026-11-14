package io.appform.dropwizard.actors.messaging;

import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.messaging.config.MessagingConfigValidator;
import lombok.experimental.UtilityClass;

/**
 * Broker names derived from the messaging config.
 *
 * <p>Exchange names exist in two forms: the <b>logical</b> name (the config key, used in code) and the <b>broker</b>
 * name (the logical name prefixed with the namespace, if one is set). {@link ExchangeNames} bundles both, plus the
 * names derived from them, so the two forms are never mixed up.
 */
@UtilityClass
class MessagingNames {

    /**
     * Names for one exchange.
     *
     * @param logical  config key, used in code, logs and metrics
     * @param broker   exchange name on the broker ({@code <namespace>.<logical>})
     * @param sideline dead-letter exchange shared by the exchange's queues (DIRECT, routed by queue name)
     */
    record ExchangeNames(String logical, String broker, String sideline) {
    }

    ExchangeNames forExchange(final String logical) {
        final String broker = NamingUtils.prefixWithNamespace(logical);
        return new ExchangeNames(logical, broker, broker + MessagingConfigValidator.SIDELINE);
    }

    /**
     * Broker queue name: {@code <namespace>.<prefix>.<queue>}.
     */
    String queue(final String prefix, final String queue) {
        return NamingUtils.queueName(prefix, queue);
    }

    String sidelineQueue(final String brokerQueue) {
        return NamingUtils.getSideline(brokerQueue);
    }

    /**
     * Private delayed exchange used by {@code MessageContext.retryLater}; bound only to {@code brokerQueue}.
     */
    String retryExchange(final String brokerQueue) {
        return brokerQueue + MessagingConfigValidator.RETRY;
    }
}
