package io.appform.dropwizard.actors.messaging.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.appform.dropwizard.actors.messaging.ExchangeType;
import io.appform.dropwizard.actors.retry.WaitMode;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessagingConfigValidatorTest {

    private static QueueSpec queue(final String... keys) {
        return QueueSpec.builder().bindingKeys(List.of(keys)).build();
    }

    private static ExchangeSpec exchange(final ExchangeType type, final Map<String, QueueSpec> queues) {
        return ExchangeSpec.builder().type(type).queues(new LinkedHashMap<>(queues)).build();
    }

    private static List<String> validate(final Map<String, ExchangeSpec> exchanges) {
        return MessagingConfigValidator.validate(
                MessagingConfig.builder().exchanges(new LinkedHashMap<>(exchanges)).build());
    }

    private static void assertSingleError(final List<String> errors, final String fragment) {
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains(fragment), errors.toString());
    }

    @Test
    void validConfigHasNoErrors() {
        assertEquals(List.of(), validate(Map.of(
                "d", exchange(ExchangeType.DIRECT, Map.of("q1", queue("a", "b"))),
                "t", exchange(ExchangeType.TOPIC, Map.of("q2", queue("order.*.PLACED", "order.#"))),
                "f", exchange(ExchangeType.FANOUT, Map.of("q3", queue())))));
    }

    @Test
    void requiresAtLeastOneExchange() {
        assertSingleError(MessagingConfigValidator.validate(null), "at least one exchange");
        assertSingleError(validate(Map.of()), "at least one exchange");
        final MessagingConfig nullExchanges = MessagingConfig.builder().exchanges(null).build();
        assertSingleError(MessagingConfigValidator.validate(nullExchanges), "at least one exchange");
    }

    @Test
    void rejectsBlankExchangeName() {
        assertSingleError(validate(Map.of(" ", exchange(ExchangeType.DIRECT, Map.of("q", queue("k"))))),
                "Exchange name must not be blank");
    }

    @Test
    void rejectsReservedExchangeSuffix() {
        assertSingleError(validate(Map.of("e_SIDELINE", exchange(ExchangeType.DIRECT, Map.of("q", queue("k"))))),
                "reserved");
    }

    @Test
    void requiresExchangeType() {
        final Map<String, ExchangeSpec> exchanges = new LinkedHashMap<>();
        exchanges.put("e", ExchangeSpec.builder().type(null).build());
        assertSingleError(validate(exchanges), "type is required");
        exchanges.put("e", null);
        assertSingleError(validate(exchanges), "type is required");
    }

    @Test
    void requiresAtLeastOneQueue() {
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.DIRECT, Map.of()))), "has no queues");
        final ExchangeSpec nullQueues = ExchangeSpec.builder().type(ExchangeType.FANOUT).queues(null).build();
        assertSingleError(validate(Map.of("e", nullQueues)), "has no queues");
    }

    @Test
    void rejectsReservedQueueSuffixes() {
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.DIRECT, Map.of("q_SIDELINE", queue("k"))))),
                "reserved");
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.DIRECT, Map.of("q_RETRY", queue("k"))))),
                "reserved");
    }

    @Test
    void rejectsBlankQueueNameAndNullSpec() {
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.DIRECT, Map.of("", queue("k"))))),
                "queue name must not be blank");
        final Map<String, QueueSpec> queues = new LinkedHashMap<>();
        queues.put("q", null);
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.DIRECT, queues))), "spec is required");
    }

    @Test
    void rejectsQueueUnderTwoExchanges() {
        final Map<String, ExchangeSpec> exchanges = new LinkedHashMap<>();
        exchanges.put("a", exchange(ExchangeType.DIRECT, Map.of("q", queue("k"))));
        exchanges.put("b", exchange(ExchangeType.DIRECT, Map.of("q", queue("k"))));
        assertSingleError(validate(exchanges), "configured under both 'a' and 'b'");
    }

    @Test
    void routingExchangesRequireBindingKeys() {
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.DIRECT, Map.of("q", queue())))),
                "at least one binding key");
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.TOPIC, Map.of("q", queue())))),
                "at least one binding key");
        final QueueSpec nullKeys = QueueSpec.builder().bindingKeys(null).build();
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.DIRECT, Map.of("q", nullKeys)))),
                "at least one binding key");
    }

    @Test
    void fanoutRejectsBindingKeys() {
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.FANOUT, Map.of("q", queue("k"))))),
                "bindingKeys must be empty");
    }

    @Test
    void rejectsBlankAndPaddedBindingKeys() {
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.DIRECT, Map.of("q", queue(" "))))),
                "must not be blank");
        assertSingleError(validate(Map.of("e", exchange(ExchangeType.DIRECT, Map.of("q", queue("k "))))),
                "surrounding whitespace");
    }

    @Test
    void rejectsTopicKeysWithEmptyWords() {
        for (final String key : List.of("a..b", ".a", "a.")) {
            assertSingleError(validate(Map.of("e", exchange(ExchangeType.TOPIC, Map.of("q", queue(key))))),
                    "invalid topic binding key");
        }
        // DIRECT keys are matched literally, so dots carry no meaning there.
        assertEquals(List.of(), validate(Map.of("e", exchange(ExchangeType.DIRECT, Map.of("q", queue("a..b"))))));
    }

    @Test
    void reportsAllErrorsTogether() {
        final Map<String, ExchangeSpec> exchanges = new LinkedHashMap<>();
        exchanges.put("x_SIDELINE", exchange(ExchangeType.FANOUT, Map.of("q1", queue("k"))));
        exchanges.put("y", exchange(ExchangeType.TOPIC, Map.of("q2_RETRY", queue("a..b"))));
        exchanges.put("z", exchange(ExchangeType.DIRECT, Map.of()));
        assertEquals(5, validate(exchanges).size());
    }

    @Test
    void rejectsAsyncRetryWaitMode() {
        final CountLimitedFixedWaitRetryConfig async =
                new CountLimitedFixedWaitRetryConfig();
        async.setWaitMode(WaitMode.ASYNC);
        final QueueSpec asyncQueue = QueueSpec.builder().retryConfig(async).build();
        final List<String> errors = validate(Map.of("e", exchange(ExchangeType.FANOUT, Map.of("q", asyncQueue))));
        assertSingleError(errors, "retryConfig.waitMode ASYNC is not supported");
        assertTrue(errors.get(0).contains("on FANOUT a retry would reach every bound queue"), errors.toString());
        assertTrue(errors.get(0).contains("on TOPIC it would usually match no binding"), errors.toString());

        final CountLimitedFixedWaitRetryConfig sync =
                new CountLimitedFixedWaitRetryConfig();
        sync.setWaitMode(WaitMode.SYNC);
        assertEquals(List.of(), validate(Map.of("e", exchange(ExchangeType.FANOUT,
                Map.of("q", QueueSpec.builder().retryConfig(sync).build())))));
        assertEquals(List.of(), validate(Map.of("e", exchange(ExchangeType.FANOUT,
                Map.of("q", QueueSpec.builder().build())))));   // default NoRetryConfig, SYNC
    }
}
