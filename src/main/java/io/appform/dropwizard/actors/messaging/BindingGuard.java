package io.appform.dropwizard.actors.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.BindingKeyMatcher;
import io.appform.dropwizard.actors.connectivity.RmqManagementClient.Binding;
import io.appform.dropwizard.actors.connectivity.RmqManagementClient;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies a queue's bindings to its exchange, guarding against binding drift through the RabbitMQ Management API
 * (on unless {@code messaging.skipBindingDriftCheck} is set).
 *
 * <p>AMQP bindings are additive and this library never unbinds, so on a durable queue a changed config would
 * silently leave stale bindings behind. With the Management API enabled, the queue's current bindings are looked up
 * once, and:
 * <ul>
 *   <li><b>stale exchange:</b> the queue must not be bound to any exchange other than its own (plus its own retry
 *       exchange). Checked before anything is declared or bound;</li>
 *   <li><b>immutable binding keys:</b> if the queue already has bindings from the exchange, they must equal the
 *       configured keys exactly, and nothing is (re)bound;</li>
 *   <li><b>concurrent creator:</b> on first creation the keys are bound and looked up again; a concurrent creator with
 *       a different config (other binding keys, or another exchange) fails startup.</li>
 * </ul>
 * If the Management API can't be read, startup fails. With the checks disabled, the keys are bound additively without
 * checks.
 *
 * <p>Used only while messaging declares its topology: {@link #close()} releases the Management API client right after,
 * so the checks can't run again later (e.g. after a reconnect).
 */
@Slf4j
class BindingGuard implements AutoCloseable {

    /**
     * Declares the queue (idempotent).
     */
    @FunctionalInterface
    interface QueueDeclarer {
        void declare() throws IOException;
    }

    /**
     * Binds the queue to the exchange with one key.
     */
    @FunctionalInterface
    interface QueueBinder {
        void bind(String bindingKey) throws IOException;
    }

    /**
     * A queue's bindings as read from the Management API.
     *
     * @param queueExists false when the queue does not exist yet (HTTP 404); {@code bindings} is then empty
     * @param bindings    the queue's bindings
     */
    private record BindingsLookup(boolean queueExists, List<Binding> bindings) {

        static BindingsLookup found(final List<Binding> bindings) {
            return new BindingsLookup(true, bindings);
        }

        static BindingsLookup queueNotFound() {
            return new BindingsLookup(false, List.of());
        }

        /**
         * Binding keys from {@code exchange}; empty when the queue does not exist or has no binding from it.
         */
        List<String> keysFrom(final String exchange) {
            return bindings.stream()
                    .filter(binding -> binding.exchange().equals(exchange))
                    .map(Binding::routingKey)
                    .toList();
        }
    }

    private final RMQConfig rmqConfig;
    private final RmqManagementClient client;

    BindingGuard(final RMQConfig rmqConfig, final ObjectMapper mapper, final boolean driftCheckEnabled) {
        this(rmqConfig, driftCheckEnabled ? new RmqManagementClient(rmqConfig, mapper) : null);
        if (client == null) {
            log.warn("Messaging binding drift checks are disabled (skipBindingDriftCheck: true): a changed bindingKeys "
                    + "or a queue moved to another exchange will keep its old bindings without any error");
        }
    }

    BindingGuard(final RMQConfig rmqConfig, final RmqManagementClient client) {
        this.rmqConfig = rmqConfig;
        this.client = client;
    }

    /**
     * Declares {@code brokerQueue} and binds it to {@code brokerExchange} with {@code bindingKeys}, applying the drift
     * checks when the Management API is enabled.
     *
     * @param brokerQueue           queue name on the broker (namespaced)
     * @param brokerExchange        exchange name on the broker (namespaced)
     * @param bindingKeys           configured binding keys
     * @param allowedOtherExchanges other exchanges the queue may legitimately be bound to (its own retry exchange)
     * @param declareQueue          declares the queue (idempotent)
     * @param bindQueue             binds the queue to the exchange with one key
     */
    void ensureBindings(final String brokerQueue,
                        final String brokerExchange,
                        final List<String> bindingKeys,
                        final Set<String> allowedOtherExchanges,
                        final QueueDeclarer declareQueue,
                        final QueueBinder bindQueue) throws Exception {
        if (client == null) {
            // Drift checks disabled: bindings are additive, so changed keys or exchanges leave old bindings behind.
            declareAndBind(declareQueue, bindingKeys, bindQueue);
            return;
        }

        // Read the current bindings before touching anything, so a drifted queue fails without being modified.
        final BindingsLookup current = lookupBindings(brokerQueue, "read");
        rejectStaleExchanges(brokerQueue, brokerExchange, allowedOtherExchanges, current);
        declareQueue.declare();

        final List<String> currentKeys = current.keysFrom(brokerExchange);
        if (!currentKeys.isEmpty()) {
            // Existing queue: its bindings are already in place. Binding the configured keys would only add new ones
            // and never remove old ones, so require an exact match instead of binding.
            requireSameKeys(bindingKeys, currentKeys, String.format(
                    "Binding keys are immutable for an existing queue. Mismatch for queue '%s' on exchange '%s'. "
                            + "To change them, use a new queue.", brokerQueue, brokerExchange));
            return;
        }

        // New queue (or not yet bound to this exchange): bind, then read again in case another instance created the
        // same queue at the same time with a different config.
        bindAll(bindingKeys, bindQueue);
        rejectConcurrentCreator(brokerQueue, brokerExchange, bindingKeys, allowedOtherExchanges);
    }

    /**
     * Fails if the queue is bound to an exchange other than its own (and the allowed ones): left over from moving the
     * queue to another exchange, or another service/instance using the same queue name on a different exchange.
     */
    private static void rejectStaleExchanges(final String queue,
                                             final String exchange,
                                             final Set<String> allowedOtherExchanges,
                                             final BindingsLookup current) {
        // The default (nameless) exchange binding every queue has is already excluded by the client.
        final Set<String> staleExchanges = current.bindings().stream()
                .map(Binding::exchange)
                .filter(source -> !source.equals(exchange) && !allowedOtherExchanges.contains(source))
                .collect(Collectors.toSet());
        if (!staleExchanges.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "Queue '%s' is bound to unexpected exchange(s) %s besides its configured exchange '%s': either a "
                            + "stale binding left by moving the queue to another exchange (use a new queue instead), "
                            + "or another service/instance using the same queue name on a different exchange (give "
                            + "each service its own messaging prefix).", queue, staleExchanges, exchange));
        }
    }

    /**
     * After a first-creation bind, looks the bindings up again: another instance creating the same queue at the same
     * time with a different config would show up as extra keys, or as a binding from another exchange.
     */
    private void rejectConcurrentCreator(final String queue,
                                         final String exchange,
                                         final List<String> bindingKeys,
                                         final Set<String> allowedOtherExchanges) {
        final BindingsLookup afterBind = lookupBindings(queue, "re-read");
        if (!afterBind.queueExists()) {
            // Deleted right after we declared it; nothing left to compare.
            return;
        }
        rejectStaleExchanges(queue, exchange, allowedOtherExchanges, afterBind);
        requireSameKeys(bindingKeys, afterBind.keysFrom(exchange), String.format(
                "Binding keys mismatch after first-creation bind for queue '%s' on exchange '%s' "
                        + "(likely a concurrent creator with different binding keys).", queue, exchange));
    }

    /**
     * Looks up the queue's bindings. Fails startup if the Management API can't be read: the drift checks are enabled,
     * so proceeding without them could silently leave stale bindings.
     *
     * @param phase "read" or "re-read", for messages
     */
    private BindingsLookup lookupBindings(final String queue, final String phase) {
        try {
            return client.fetchBindings(vhost(), queue)
                    .map(BindingsLookup::found)
                    .orElseGet(BindingsLookup::queueNotFound);
        } catch (RuntimeException e) {
            throw new IllegalStateException(String.format(
                    "Unable to %s bindings from the RabbitMQ Management API for queue '%s': %s. The binding drift "
                            + "checks (on by default) need the rabbitmq_management plugin, network access to its port "
                            + "(managementConfig.port, default 15672) and a RabbitMQ user with a management tag (e.g. "
                            + "monitoring). Fix that, or set messaging.skipBindingDriftCheck: true to skip the checks "
                            + "(changed bindings then go undetected).", phase, queue, e.getMessage()), e);
        }
    }

    private static void declareAndBind(final QueueDeclarer declareQueue,
                                       final List<String> bindingKeys,
                                       final QueueBinder bindQueue) throws Exception {
        declareQueue.declare();
        bindAll(bindingKeys, bindQueue);
    }

    private static void bindAll(final List<String> keys, final QueueBinder bindQueue) throws Exception {
        for (final String key : keys) {
            bindQueue.bind(key);
        }
    }

    private static void requireSameKeys(final List<String> configured,
                                        final List<String> actual,
                                        final String reason) {
        if (!BindingKeyMatcher.matches(configured, actual)) {
            throw new IllegalStateException(String.format("%s configured=%s, actual=%s", reason,
                    BindingKeyMatcher.normalize(configured), BindingKeyMatcher.normalize(actual)));
        }
    }

    private String vhost() {
        final String vhost = rmqConfig.getVirtualHost();
        return vhost == null ? "/" : vhost;
    }

    /**
     * Releases the Management API client. Idempotent; the checks can't run afterwards.
     */
    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
