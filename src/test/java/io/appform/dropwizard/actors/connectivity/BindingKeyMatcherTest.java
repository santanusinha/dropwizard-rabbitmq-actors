package io.appform.dropwizard.actors.connectivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BindingKeyMatcherTest {

    @Test
    void testNormalizeNull() {
        Assertions.assertTrue(BindingKeyMatcher.normalize(null).isEmpty());
    }

    @Test
    void testNormalizeDedupsAndDropsNullsWithoutTrimming() {
        final List<String> keys = new ArrayList<>(List.of(" order.* ", "order.*", "order.*",
                "payment.created"));
        keys.add(null);
        Assertions.assertEquals(Set.of(" order.* ", "order.*", "payment.created"),
                BindingKeyMatcher.normalize(keys));
    }

    @Test
    void testWhitespaceIsSignificant() {
        Assertions.assertFalse(BindingKeyMatcher.matches(List.of("order.*"), List.of(" order.*")));
    }

    @Test
    void testMatchesIgnoresOrderAndDuplicates() {
        Assertions.assertTrue(BindingKeyMatcher.matches(
                List.of("order.*", "payment.created"),
                List.of("payment.created", "order.*", "order.*")));
    }

    @Test
    void testMismatchOnAddedKey() {
        Assertions.assertFalse(BindingKeyMatcher.matches(
                List.of("order.*"),
                List.of("order.*", "payment.created")));
    }

    @Test
    void testMismatchOnRemovedKey() {
        Assertions.assertFalse(BindingKeyMatcher.matches(
                List.of("order.*", "payment.created"),
                List.of("order.*")));
    }

    @Test
    void testMismatchOnAddedKeyIsAlsoRejected() {
        // Exact-match contract: even a pure addition (existing subset of configured) is a mismatch, so
        // changing an established queue's bindingKeys always fails - documented, deliberate behaviour.
        Assertions.assertFalse(BindingKeyMatcher.matches(
                List.of("A", "B"),   // configured (added B)
                List.of("A")));      // existing
    }

    @Test
    void testMatchesEmptySets() {
        Assertions.assertTrue(BindingKeyMatcher.matches(List.of(), List.of()));
    }
}
