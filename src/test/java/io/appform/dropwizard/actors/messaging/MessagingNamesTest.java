package io.appform.dropwizard.actors.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.messaging.MessagingNames.ExchangeNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagingNamesTest {

    @AfterEach
    void clearNamespace() {
        System.clearProperty(NamingUtils.NAMESPACE_PROPERTY_NAME);
    }

    @Test
    void exchangeNamesWithoutNamespace() {
        assertEquals(new ExchangeNames("ex", "ex", "ex_SIDELINE"), MessagingNames.forExchange("ex"));
    }

    @Test
    void namespacePrefixesBrokerNamesButNotLogical() {
        System.setProperty(NamingUtils.NAMESPACE_PROPERTY_NAME, "ns");
        final ExchangeNames names = MessagingNames.forExchange("ex");
        assertEquals("ex", names.logical());
        assertEquals("ns.ex", names.broker());
        assertEquals("ns.ex_SIDELINE", names.sideline());
    }

    @Test
    void derivedQueueNames() {
        final String queue = MessagingNames.queue("p", "Q");
        assertEquals(NamingUtils.queueName("p", "Q"), queue);
        assertEquals(queue + "_SIDELINE", MessagingNames.sidelineQueue(queue));
        assertEquals(queue + "_RETRY", MessagingNames.retryExchange(queue));
    }
}
