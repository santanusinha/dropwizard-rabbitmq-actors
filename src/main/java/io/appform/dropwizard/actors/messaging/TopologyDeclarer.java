package io.appform.dropwizard.actors.messaging;

import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.messaging.MessagingNames.ExchangeNames;
import io.appform.dropwizard.actors.messaging.config.ExchangeSpec;
import io.appform.dropwizard.actors.messaging.config.QueueSpec;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

/**
 * Declares an exchange and everything bound to it, in this order:
 * <ol>
 *   <li>the exchange (optionally {@code x-delayed-message});</li>
 *   <li>the shared sideline (dead-letter) exchange (DIRECT);</li>
 *   <li>per queue: main queue (dead-lettering to its sideline queue) with its bindings (drift-guarded, checked before
 *       anything else is declared for the queue), sideline queue and the
 *       private retry exchange (if {@code retryLaterEnabled}).</li>
 * </ol>
 * This replaces, for messaging, what {@code UnmanagedPublisher.start()} does for a single actor. Actor topology is not
 * touched.
 *
 * <p>Declarations run on a dedicated channel per exchange: the broker closes a channel on any declaration error (e.g.
 * {@code 406 PRECONDITION_FAILED}), and that must not take down the connection's shared channel used by actors. The
 * channel stays open until {@link #close()}, because the client's automatic topology recovery redeclares on the
 * channel each entity was originally declared on.
 */
@Slf4j
class TopologyDeclarer {

    static final String DELAYED_EXCHANGE_TYPE = "x-delayed-message";
    static final String DELAYED_TYPE_ARG = "x-delayed-type";
    static final String HA_MODE_ARG = "ha-mode";
    static final String HA_PARAMS_ARG = "ha-params";

    private final String prefix;
    private final BindingGuard bindingGuard;
    private final List<Channel> declarationChannels = new CopyOnWriteArrayList<>();

    TopologyDeclarer(final String prefix, final BindingGuard bindingGuard) {
        this.prefix = prefix;
        this.bindingGuard = bindingGuard;
    }

    /**
     * Declares one exchange and everything bound to it, on a dedicated channel kept open for recovery. Called once
     * per configured exchange when messaging starts; the config is expected to be validated already.
     *
     * @param names      logical and broker names for the exchange
     * @param spec       the exchange's config
     * @param connection connection to declare on (the exchange's producer connection)
     */
    void declare(final ExchangeNames names, final ExchangeSpec spec, final RMQConnection connection)
            throws Exception {
        final Channel channel = connection.newChannel();
        try {
            declareExchange(channel, names, spec);
            for (final Map.Entry<String, QueueSpec> entry : spec.getQueues().entrySet()) {
                declareQueue(channel, connection, names, spec, entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            // A failed declaration has already closed the channel; closing it again would hide the real error.
            closeQuietly(channel);
            throw e;
        }
        // Kept open on purpose: the client's automatic recovery redeclares each recorded exchange, queue and binding
        // on the channel it was declared on. Closing it would make every redeclare fail after a reconnect.
        declarationChannels.add(channel);
    }

    /**
     * Called once every exchange is declared (or declaration failed): releases the binding guard's Management API
     * client, which is only needed while declaring. The declaration channels stay open until {@link #close()}.
     * Idempotent.
     */
    void declarationFinished() {
        try {
            bindingGuard.close();
        } catch (Exception e) {
            log.warn("Error closing the binding drift guard", e);
        }
    }

    /**
     * Closes the channels kept open for topology recovery (and the binding guard, if still open). Called when
     * messaging stops.
     */
    synchronized void close() {
        declarationFinished();
        declarationChannels.forEach(TopologyDeclarer::closeQuietly);
        declarationChannels.clear();
    }

    private static void closeQuietly(final Channel channel) {
        try {
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (Exception e) {
            log.warn("Error closing topology declaration channel", e);
        }
    }

    private void declareExchange(final Channel channel, final ExchangeNames names, final ExchangeSpec spec)
            throws IOException {
        // 1. The exchange itself, durable and never auto-deleted. A delayed exchange is an x-delayed-message exchange
        //    that holds each message for its x-delay, then routes it like the configured type (x-delayed-type).
        //    Exchange type and arguments are immutable: changing them on an existing exchange fails the redeclare (406).
        if (spec.isDelayed()) {
            channel.exchangeDeclare(names.broker(), DELAYED_EXCHANGE_TYPE, true, false,
                    Map.of(DELAYED_TYPE_ARG, spec.getType().getAmqpType()));
        } else {
            channel.exchangeDeclare(names.broker(), spec.getType().getAmqpType(), true);
        }
        log.info("Declared exchange {} (broker {}) type={} delayed={}", names.logical(), names.broker(),
                spec.getType(), spec.isDelayed());

        // 2. The sideline (dead-letter) exchange shared by the exchange's queues. Always DIRECT, whatever the exchange
        //    type: each queue dead-letters with its own name as routing key, reaching only its own sideline queue.
        channel.exchangeDeclare(names.sideline(), ExchangeType.DIRECT.getAmqpType(), true);
    }

    /**
     * Declares one queue: the queue itself with its bindings (drift-guarded), its sideline queue, and its retry exchange
     * if enabled.
     */
    private void declareQueue(final Channel channel,
                              final RMQConnection connection,
                              final ExchangeNames names,
                              final ExchangeSpec spec,
                              final String queue,
                              final QueueSpec queueSpec) throws Exception {
        final String brokerQueue = MessagingNames.queue(prefix, queue);
        final Map<String, Object> baseArgs = queueArguments(connection, names, queueSpec);

        // 1. The queue itself, dead-lettering to the sideline exchange, and its bindings through the drift guard. This
        //    runs first: if the guard rejects the queue (changed keys, moved to another exchange), startup fails before
        //    anything else is declared for it, since the library never removes a binding it has added. The dead-letter
        //    routing key is pinned to the queue name: the message's own routing key (a topic key, or empty for fanout)
        //    would not match the sideline binding. FANOUT ignores routing keys, so it binds with "".
        final Map<String, Object> mainArgs = new HashMap<>(baseArgs);
        mainArgs.put("x-dead-letter-exchange", names.sideline());
        mainArgs.put("x-dead-letter-routing-key", brokerQueue);
        final List<String> bindingKeys = spec.getType().usesRoutingKey() ? queueSpec.getBindingKeys() : List.of("");
        final String retryExchange = MessagingNames.retryExchange(brokerQueue);
        // The queue's own retry exchange is always allowed, whatever retryLaterEnabled says: the library never removes
        // the binding, so after the flag is turned off it stays behind. It is harmless (nothing publishes to that
        // exchange any more) and only this library creates that name.
        final Set<String> allowedOtherExchanges = Set.of(retryExchange);
        bindingGuard.ensureBindings(brokerQueue, names.broker(), bindingKeys, allowedOtherExchanges,
                () -> channel.queueDeclare(brokerQueue, true, false, false, mainArgs),
                key -> channel.queueBind(brokerQueue, names.broker(), key));
        log.info("Declared queue {} on exchange {} with binding keys {}", brokerQueue, names.broker(), bindingKeys);

        // 2. Sideline queue. Declared before any consumer starts, so a rejected message always has somewhere to go.
        declareSidelineQueue(channel, names, brokerQueue, baseArgs);

        // 3. Private retry exchange for MessageContext.retryLater.
        if (queueSpec.isRetryLaterEnabled()) {
            declareRetryExchange(channel, brokerQueue, retryExchange);
        }
    }

    /**
     * Declares {@code <queue>_SIDELINE} with the same arguments as the queue (no dead-lettering of its own), bound to
     * the exchange's sideline exchange by the queue name.
     */
    private static void declareSidelineQueue(final Channel channel,
                                             final ExchangeNames names,
                                             final String brokerQueue,
                                             final Map<String, Object> baseArgs) throws IOException {
        final String sidelineQueue = MessagingNames.sidelineQueue(brokerQueue);
        channel.queueDeclare(sidelineQueue, true, false, false, baseArgs);
        channel.queueBind(sidelineQueue, names.sideline(), brokerQueue);
    }

    /**
     * Declares the queue's private delayed exchange (routing DIRECT) and binds only this queue to it, by its name, so a
     * retry never reaches the exchange's other queues.
     */
    private static void declareRetryExchange(final Channel channel,
                                             final String brokerQueue,
                                             final String retryExchange) throws IOException {
        channel.exchangeDeclare(retryExchange, DELAYED_EXCHANGE_TYPE, true, false,
                Map.of(DELAYED_TYPE_ARG, ExchangeType.DIRECT.getAmqpType()));
        channel.queueBind(brokerQueue, retryExchange, brokerQueue);
        log.info("Declared retry exchange {} for queue {}", retryExchange, brokerQueue);
    }

    /**
     * Queue arguments built by the same code as for actors, minus {@code ha-mode}/{@code ha-params}: the actor builder
     * always adds them for classic queues, but as queue arguments they don't enable mirroring (RabbitMQ mirrors classic
     * queues only through policies), so messaging queues are declared without them.
     */
    private static Map<String, Object> queueArguments(final RMQConnection connection,
                                                      final ExchangeNames names,
                                                      final QueueSpec queueSpec) {
        final Map<String, Object> args = new HashMap<>(
                connection.rmqOpts(queueSpec.toQueueArgumentsConfig(names.broker())));
        args.remove(HA_MODE_ARG);
        args.remove(HA_PARAMS_ARG);
        return args;
    }
}
