package io.appform.dropwizard.actors.messaging.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.appform.dropwizard.actors.messaging.ExchangeType;
import io.dropwizard.jackson.Jackson;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Messaging config as Dropwizard reads it from YAML: the binding drift checks stay on unless explicitly skipped.
 */
class MessagingConfigYamlTest {

    private static final ObjectMapper YAML = Jackson.newObjectMapper(new YAMLFactory());

    private static final String EXCHANGES = """
            exchanges:
              ex:
                type: DIRECT
                queues:
                  q:
                    bindingKeys: ["k"]
            """;

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

    @Test
    void asyncRetryInYamlFailsValidation() throws Exception {
        final MessagingConfig config = read("""
                exchanges:
                  ex:
                    type: FANOUT
                    queues:
                      q:
                        retryConfig:
                          type: COUNT_LIMITED_FIXED_WAIT
                          maxAttempts: 3
                          waitTime: 1s
                          waitMode: ASYNC
                """);
        final List<String> errors = MessagingConfigValidator.validate(config);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("waitMode ASYNC is not supported"), errors.toString());
    }
}
