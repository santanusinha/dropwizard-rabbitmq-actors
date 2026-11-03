package io.appform.dropwizard.actors.messaging.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.appform.dropwizard.actors.messaging.ExchangeType;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.Test;

/**
 * Messaging config as Dropwizard reads it from YAML: the binding drift checks stay on unless explicitly skipped.
 */
class MessagingConfigYamlTest {

    private static final ObjectMapper YAML = Jackson.newObjectMapper(new YAMLFactory());

    private static final String EXCHANGES = "exchanges:\n"
            + "  ex:\n"
            + "    type: DIRECT\n"
            + "    queues:\n"
            + "      q:\n"
            + "        bindingKeys: [\"k\"]\n";

    private static MessagingConfig read(final String yaml) throws Exception {
        return YAML.readValue(yaml, MessagingConfig.class);
    }

    @Test
    void missingSkipFlagKeepsDriftChecksOn() throws Exception {
        final MessagingConfig config = read(EXCHANGES);
        assertFalse(config.isSkipBindingDriftCheck());
        assertEquals("rabbitmq.messaging", config.getPrefix());
        assertEquals(ExchangeType.DIRECT, config.getExchanges().get("ex").getType());
    }

    @Test
    void emptySkipFlagKeepsDriftChecksOn() throws Exception {
        assertFalse(read("skipBindingDriftCheck:\n" + EXCHANGES).isSkipBindingDriftCheck());
    }

    @Test
    void explicitSkipTurnsDriftChecksOff() throws Exception {
        assertTrue(read("skipBindingDriftCheck: true\n" + EXCHANGES).isSkipBindingDriftCheck());
        assertFalse(read("skipBindingDriftCheck: false\n" + EXCHANGES).isSkipBindingDriftCheck());
    }
}
