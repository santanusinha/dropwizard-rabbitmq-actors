package io.appform.dropwizard.actors.connectivity;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalizes and compares binding-key sets for the immutable {@code bindingKeys} check.
 *
 * <p>Comparison is order- and duplicate-insensitive: both the configured (effective) binding keys and
 * the keys read from the Management API are normalized to a {@link Set} (nulls dropped) before an exact
 * set-equality comparison. Keys are compared as-is (not trimmed), exactly as they are bound; the messaging config
 * validator rejects keys with surrounding whitespace.
 */
@UtilityClass
public class BindingKeyMatcher {

    public Set<String> normalize(final Collection<String> keys) {
        if (keys == null) {
            return Set.of();
        }
        return keys.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * @return true iff the normalized configured set exactly equals the normalized existing set.
     */
    public boolean matches(final Collection<String> configured, final Collection<String> existing) {
        return normalize(configured).equals(normalize(existing));
    }
}
