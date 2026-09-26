package io.appform.dropwizard.actors.connectivity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class BindingKeyMatcherTest {

    @Test
    void testNormalizeNull() {
        Assertions.assertTrue(BindingKeyMatcher.normalize(null).isEmpty());
    }

    @Test
    void testNormalizeTrimsAndDedups() {
        Assertions.assertEquals(java.util.Set.of("order.*", "payment.created"),
                BindingKeyMatcher.normalize(List.of(" order.* ", "order.*", "payment.created")));
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
