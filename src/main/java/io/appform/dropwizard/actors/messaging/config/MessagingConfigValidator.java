package io.appform.dropwizard.actors.messaging.config;

import io.appform.dropwizard.actors.messaging.ExchangeType;
import io.appform.dropwizard.actors.retry.WaitMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * Structural validation of a {@link MessagingConfig}. Returns human readable errors; empty means valid.
 *
 * <p>Rules:
 * <ul>
 *   <li>at least one exchange is configured;</li>
 *   <li>exchange and queue names are non-blank, don't end with a suffix the library uses for derived names
 *       ({@value #SIDELINE}, {@value #RETRY}), and a queue name is used under only one
 *       exchange;</li>
 *   <li>every exchange has a type and at least one queue (publish-only exchanges are not supported);</li>
 *   <li>DIRECT/TOPIC queues have at least one binding key; FANOUT queues have none;</li>
 *   <li>binding keys are non-blank with no surrounding whitespace; TOPIC keys contain no empty words
 *       (e.g. {@code a..b});</li>
 *   <li>{@code retryConfig.waitMode} is not {@code ASYNC}: actor async retries dead-letter back through the exchange
 *       with the queue name as routing key, so on FANOUT a retry would reach every bound queue, and on TOPIC it would
 *       usually match no binding and be dropped.</li>
 * </ul>
 */
@UtilityClass
public class MessagingConfigValidator {

    public static final String SIDELINE = "_SIDELINE";
    public static final String RETRY = "_RETRY";

    public List<String> validate(final MessagingConfig config) {
        final List<String> errors = new ArrayList<>();
        if (config == null || config.getExchanges() == null || config.getExchanges().isEmpty()) {
            errors.add("messaging must configure at least one exchange");
            return errors;
        }
        final Map<String, String> queueOwner = new HashMap<>();
        config.getExchanges().forEach((exchange, spec) -> {
            if (isBlank(exchange)) {
                errors.add("Exchange name must not be blank");
                return;
            }
            if (exchange.endsWith(SIDELINE)) {
                errors.add(String.format("Exchange '%s': names ending with %s are reserved", exchange, SIDELINE));
            }
            if (spec == null || spec.getType() == null) {
                errors.add(String.format("Exchange '%s': type is required", exchange));
                return;
            }
            final Map<String, QueueSpec> queues = Objects.requireNonNullElse(spec.getQueues(), Map.of());
            if (queues.isEmpty()) {
                errors.add(String.format("Exchange '%s' has no queues; configure at least one queue consumed by "
                        + "this service", exchange));
            }
            queues.forEach((queue, queueSpec) ->
                    validateQueue(exchange, spec.getType(), queue, queueSpec, queueOwner, errors));
        });
        return errors;
    }

    private void validateQueue(final String exchange,
                               final ExchangeType type,
                               final String queue,
                               final QueueSpec queueSpec,
                               final Map<String, String> queueOwner,
                               final List<String> errors) {
        if (isBlank(queue)) {
            errors.add(String.format("Exchange '%s': queue name must not be blank", exchange));
            return;
        }
        if (queue.endsWith(SIDELINE) || queue.endsWith(RETRY)) {
            errors.add(String.format("Queue '%s': names ending with %s or %s are reserved", queue, SIDELINE, RETRY));
        }
        final String previousOwner = queueOwner.putIfAbsent(queue, exchange);
        if (previousOwner != null) {
            errors.add(String.format("Queue '%s' is configured under both '%s' and '%s'",
                    queue, previousOwner, exchange));
        }
        if (queueSpec == null) {
            errors.add(String.format("Exchange '%s', queue '%s': spec is required", exchange, queue));
            return;
        }
        validateRetry(exchange, queue, queueSpec, errors);
        validateBindingKeys(exchange, type, queue,
                Objects.requireNonNullElse(queueSpec.getBindingKeys(), List.of()), errors);
    }

    private void validateRetry(final String exchange,
                               final String queue,
                               final QueueSpec queueSpec,
                               final List<String> errors) {
        if (queueSpec.getRetryConfig() != null && queueSpec.getRetryConfig().getWaitMode() == WaitMode.ASYNC) {
            errors.add(String.format("Exchange '%s', queue '%s': retryConfig.waitMode ASYNC is not supported for "
                    + "messaging (async retries are dead-lettered back through the exchange with the queue name as "
                    + "routing key: on FANOUT a retry would reach every bound queue, on TOPIC it would usually match no "
                    + "binding and be dropped); use SYNC, or MessageContext.retryLater to redeliver to this queue only",
                    exchange, queue));
        }
    }

    private void validateBindingKeys(final String exchange,
                                     final ExchangeType type,
                                     final String queue,
                                     final List<String> keys,
                                     final List<String> errors) {
        if (!type.usesRoutingKey()) {
            if (!keys.isEmpty()) {
                errors.add(String.format("Exchange '%s' (FANOUT), queue '%s': bindingKeys must be empty, "
                        + "FANOUT ignores routing keys", exchange, queue));
            }
            return;
        }
        if (keys.isEmpty()) {
            errors.add(String.format("Exchange '%s' (%s), queue '%s': at least one binding key is required",
                    exchange, type, queue));
        }
        for (final String key : keys) {
            validateBindingKey(exchange, type, queue, key, errors);
        }
    }

    private void validateBindingKey(final String exchange,
                                    final ExchangeType type,
                                    final String queue,
                                    final String key,
                                    final List<String> errors) {
        if (isBlank(key)) {
            errors.add(String.format("Exchange '%s', queue '%s': binding keys must not be blank", exchange, queue));
        } else if (!key.equals(key.strip())) {
            errors.add(String.format("Exchange '%s', queue '%s': binding key '%s' has surrounding whitespace",
                    exchange, queue, key));
        } else if (type == ExchangeType.TOPIC && hasEmptyWord(key)) {
            errors.add(String.format("Exchange '%s', queue '%s': invalid topic binding key '%s'",
                    exchange, queue, key));
        }
    }

    private boolean hasEmptyWord(final String key) {
        for (final String word : key.split("\\.", -1)) {
            if (word.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
