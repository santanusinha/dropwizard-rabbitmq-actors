package io.appform.dropwizard.actors.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.messaging.config.ExchangeSpec;
import io.appform.dropwizard.actors.messaging.config.MessagingConfig;
import io.appform.dropwizard.actors.messaging.config.MessagingConfigValidator;
import io.appform.dropwizard.actors.messaging.config.QueueSpec;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.dropwizard.lifecycle.Managed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Exchange-centric messaging: application code publishes messages to exchanges, and handlers consume from the queues
 * bound to those exchanges. Publishers and consumers are separate, so a call site always names the exchange it
 * publishes to, and the configured bindings alone decide which queues receive a message.
 *
 * <p>Register publishers and handlers before the application starts (e.g. in {@code Application.run}):
 * <pre>
 * MessagePublisher&lt;OrderMessage&gt; publisher = messaging.publisher("order_events", OrderMessage::routingKey);
 * messaging.consumer("order_events", "shipping", OrderMessage.class, (message, context) -&gt; ship(message));
 * </pre>
 *
 * <p>{@link #start()} then:
 * <ol>
 *   <li>checks that every configured queue has a handler (every exchange has at least one queue, so an exchange is
 *       only published to when this service also consumes from it);</li>
 *   <li>declares the whole topology (exchanges, queues, bindings, sideline and retry), so bindings
 *       exist before the first publish;</li>
 *   <li>starts the publishers.</li>
 * </ol>
 * Handlers start receiving messages only with {@link #startConsumers()}, and stop with {@link #stopConsumers()}.
 * {@link io.appform.dropwizard.actors.RabbitmqActorBundle} calls them when the server has started (after every managed
 * object) and when it begins stopping (before any managed object), so handlers only run while the whole application
 * is up. Publishers are available from {@link #start()} to {@link #stop()}.
 *
 * <p>Messaging declares only what is in its config; actor topology is untouched. A queue belongs either to an actor
 * or to messaging, never both.
 */
@Slf4j
public class Messaging implements Managed {

    private final MessagingConfig config;
    private final ConnectionRegistry connectionRegistry;
    private final ObjectMapper mapper;
    private final RetryStrategyFactory retryStrategyFactory;
    private final ExceptionHandlingFactory exceptionHandlingFactory;
    private final TopologyDeclarer topologyDeclarer;

    /**
     * Lifecycle: publishers and handlers are registered while {@code NEW}; {@link #start()} moves to {@code STARTED}.
     * Once {@code STOPPED} (by {@link #stop()} or a failed start) it can't be restarted: the underlying consumers keep
     * their old channel handlers, so a restart would leave stale ones behind.
     */
    private enum State {
        NEW,
        STARTED,
        STOPPED
    }

    private final List<MessagePublisher<?>> publishers = new ArrayList<>();
    private final Map<String, QueueConsumer<?>> consumers = new LinkedHashMap<>();
    private volatile State state = State.NEW;
    /**
     * Consumers run between {@link #startConsumers()} and {@link #stopConsumers()}, inside {@code STARTED}.
     */
    private boolean consuming;
    private boolean consumersStopped;

    public Messaging(final MessagingConfig config,
                     final RMQConfig rmqConfig,
                     final ConnectionRegistry connectionRegistry,
                     final ObjectMapper mapper) {
        this(config, connectionRegistry, mapper, new RetryStrategyFactory(), new ExceptionHandlingFactory(),
                new TopologyDeclarer(config.getPrefix(),
                        new BindingGuard(rmqConfig, mapper, !config.isSkipBindingDriftCheck())));
    }

    Messaging(final MessagingConfig config,
              final ConnectionRegistry connectionRegistry,
              final ObjectMapper mapper,
              final RetryStrategyFactory retryStrategyFactory,
              final ExceptionHandlingFactory exceptionHandlingFactory,
              final TopologyDeclarer topologyDeclarer) {
        final List<String> errors = MessagingConfigValidator.validate(config);
        Preconditions.checkArgument(errors.isEmpty(), "Invalid messaging config: %s", errors);
        this.config = config;
        this.connectionRegistry = connectionRegistry;
        this.mapper = mapper;
        this.retryStrategyFactory = retryStrategyFactory;
        this.exceptionHandlingFactory = exceptionHandlingFactory;
        this.topologyDeclarer = topologyDeclarer;
    }

    /**
     * Creates a publisher for a DIRECT or TOPIC exchange. Must be called before {@link #start()}. Each call opens its
     * own channel when messaging starts, so create one publisher per exchange (or per message type) and reuse it.
     *
     * @param exchange           exchange name as configured under {@code messaging.exchanges}
     * @param routingKeyResolver derives each message's routing key
     * @throws IllegalArgumentException if the exchange is not configured, or is FANOUT (use
     *                                  {@link #publisher(String)})
     * @throws IllegalStateException    if messaging has already started or stopped
     */
    public synchronized <M> MessagePublisher<M> publisher(final String exchange,
                                                        final RoutingKeyResolver<M> routingKeyResolver) {
        Preconditions.checkNotNull(routingKeyResolver, "routingKeyResolver");
        return register(exchange, routingKeyResolver);
    }

    /**
     * Creates a publisher for a FANOUT exchange (no routing key). Must be called before {@link #start()}. Each call
     * opens its own channel when messaging starts, so create one publisher per exchange and reuse it.
     *
     * @param exchange exchange name as configured under {@code messaging.exchanges}
     * @throws IllegalArgumentException if the exchange is not configured, or is not FANOUT
     * @throws IllegalStateException    if messaging has already started or stopped
     */
    public synchronized <M> MessagePublisher<M> publisher(final String exchange) {
        return register(exchange, null);
    }

    /**
     * Registers the handler for a configured queue. Must be called before {@link #start()}. Every configured queue
     * needs exactly one handler, or {@link #start()} fails.
     *
     * @param exchange    exchange name as configured under {@code messaging.exchanges}
     * @param queue       queue name as configured under that exchange's {@code queues}
     * @param messageType class messages are deserialized into
     * @param handler     handles each message
     * @throws IllegalArgumentException if the exchange or queue is not configured, or the queue already has a handler
     * @throws IllegalStateException    if messaging has already started or stopped
     */
    public synchronized <M> void consumer(final String exchange,
                                          final String queue,
                                          final Class<? extends M> messageType,
                                          final QueueHandler<M> handler) {
        checkNotStarted();
        Preconditions.checkNotNull(messageType, "messageType");
        Preconditions.checkNotNull(handler, "handler");
        final ExchangeSpec exchangeSpec = exchangeSpec(exchange);
        final QueueSpec queueSpec = exchangeSpec.getQueues().get(queue);
        Preconditions.checkArgument(queueSpec != null, "Queue '%s' is not configured under exchange '%s'",
                queue, exchange);
        Preconditions.checkArgument(!consumers.containsKey(queue), "A handler is already registered for queue '%s'",
                queue);
        consumers.put(queue, new QueueConsumer<>(config.getPrefix(), queue, exchangeSpec, queueSpec, messageType,
                handler,
                connectionRegistry.createOrGet(NamingUtils.consumerConnectionName(queueSpec.getConsumer())),
                producerConnection(exchangeSpec), mapper, retryStrategyFactory, exceptionHandlingFactory));
    }

    /**
     * Declares the whole topology and starts the publishers (including the queues' {@code retryLater} publishers).
     * Consumers are <b>not</b> started here: call {@link #startConsumers()} once the rest of the application is up.
     * {@link io.appform.dropwizard.actors.RabbitmqActorBundle} does that automatically when the server has started.
     */
    @Override
    public synchronized void start() throws Exception {
        checkNotStarted();

        // 1. Every configured queue needs a handler; checked before touching the broker.
        final List<String> unhandled = config.getExchanges().values().stream()
                .flatMap(spec -> spec.getQueues().keySet().stream())
                .filter(queue -> !consumers.containsKey(queue))
                .collect(Collectors.toList());
        Preconditions.checkState(unhandled.isEmpty(),
                "No handler registered for configured queue(s) %s; messages routed to them would never be consumed",
                unhandled);

        final List<MessagePublisher<?>> startedPublishers = new ArrayList<>();
        final List<QueueConsumer<?>> consumersWithRetryPublisher = new ArrayList<>();
        try {
            // 2. The whole topology, so every binding exists before the first publish or consume.
            try {
                for (final Map.Entry<String, ExchangeSpec> entry : config.getExchanges().entrySet()) {
                    topologyDeclarer.declare(names(entry.getKey()), entry.getValue(),
                            producerConnection(entry.getValue()));
                }
            } finally {
                // The Management API (binding drift checks) is only needed while declaring.
                topologyDeclarer.declarationFinished();
            }
            // 3. Publishers, including retryLater publishers, so the application can publish while it starts.
            for (final MessagePublisher<?> publisher : publishers) {
                publisher.start();
                startedPublishers.add(publisher);
            }
            for (final QueueConsumer<?> consumer : consumers.values()) {
                consumer.startRetryPublisher();
                consumersWithRetryPublisher.add(consumer);
            }
        } catch (Exception e) {
            // 4. On failure, stop only what actually started, then fail.
            log.error("Messaging failed to start; stopping what was started", e);
            state = State.STOPPED;
            consumersStopped = true;
            stopPublishing(consumersWithRetryPublisher, startedPublishers);
            topologyDeclarer.close();
            throw e;
        }
        state = State.STARTED;
        log.info("Messaging started: exchanges={} publishers={}; consumers {} start with startConsumers()",
                config.getExchanges().keySet(), publishers.size(), consumers.keySet());
    }

    /**
     * Subscribes every queue's handler. Requires {@link #start()} first; a no-op if consumers are already running.
     *
     * <p>Kept separate from {@link #start()} so handlers only receive messages once everything they depend on has
     * started: the bundle calls this after the server has started, i.e. after every managed object.
     *
     * @throws IllegalStateException if messaging is not started, or consumers were already stopped (they can't be
     *                               restarted)
     */
    public synchronized void startConsumers() throws Exception {
        Preconditions.checkState(state == State.STARTED, "Messaging must be started before its consumers");
        if (consuming) {
            return;
        }
        Preconditions.checkState(!consumersStopped, "Messaging consumers were stopped and can't be restarted");
        final List<QueueConsumer<?>> started = new ArrayList<>();
        try {
            for (final QueueConsumer<?> consumer : consumers.values()) {
                consumer.startConsuming();
                started.add(consumer);
            }
        } catch (Exception e) {
            log.error("Messaging consumers failed to start; stopping the ones that started", e);
            consumersStopped = true;
            started.forEach(QueueConsumer::stopConsuming);
            throw e;
        }
        consuming = true;
        log.info("Messaging consumers started: {}", consumers.keySet());
    }

    /**
     * Stops consuming: cancels every subscription and waits for in-flight messages, so no handler runs afterwards.
     * Publishers keep working until {@link #stop()}. Idempotent; consumers can't be restarted.
     *
     * <p>The bundle calls this when the server begins stopping, before any managed object is stopped, so handlers
     * never run against resources that are already shut down.
     */
    public synchronized void stopConsumers() {
        if (!consuming) {
            consumersStopped = true;
            return;
        }
        consuming = false;
        consumersStopped = true;
        for (final QueueConsumer<?> consumer : consumers.values()) {
            try {
                consumer.stopConsuming();
            } catch (Exception e) {
                log.error("Error stopping consumer", e);
            }
        }
        log.info("Messaging consumers stopped: {}", consumers.keySet());
    }

    /**
     * Stops consumers (if still running), then the retry publishers and publishers. Messaging can't be started again
     * afterwards.
     */
    @Override
    public synchronized void stop() throws Exception {
        if (state == State.STOPPED) {
            return;
        }
        state = State.STOPPED;
        try {
            stopConsumers();
            stopPublishing(consumers.values(), publishers);
        } finally {
            topologyDeclarer.close();
        }
        log.info("Messaging stopped: exchanges={}", config.getExchanges().keySet());
    }

    /**
     * Stops the queues' retry publishers, then the publishers. Keeps going on failure and rethrows the first error at
     * the end.
     */
    private static void stopPublishing(final Collection<? extends QueueConsumer<?>> consumersWithRetryPublisher,
                                       final Collection<? extends MessagePublisher<?>> publishersToStop)
            throws Exception {
        Exception first = null;
        for (final QueueConsumer<?> consumer : consumersWithRetryPublisher) {
            try {
                consumer.stopRetryPublisher();
            } catch (Exception e) {
                first = first == null ? e : first;
                log.error("Error stopping retry publisher", e);
            }
        }
        for (final MessagePublisher<?> publisher : publishersToStop) {
            try {
                publisher.stop();
            } catch (Exception e) {
                first = first == null ? e : first;
                log.error("Error stopping publisher for exchange {}", publisher.getExchange(), e);
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private <M> MessagePublisher<M> register(final String exchange, final RoutingKeyResolver<M> routingKeyResolver) {
        checkNotStarted();
        final ExchangeSpec spec = exchangeSpec(exchange);
        final MessagePublisher<M> publisher = new MessagePublisher<>(names(exchange), spec, routingKeyResolver,
                producerConnection(spec), mapper);
        publishers.add(publisher);
        return publisher;
    }

    private MessagingNames.ExchangeNames names(final String exchange) {
        return MessagingNames.forExchange(exchange);
    }

    private ExchangeSpec exchangeSpec(final String exchange) {
        final ExchangeSpec spec = config.getExchanges().get(exchange);
        Preconditions.checkArgument(spec != null, "Exchange '%s' is not configured under messaging.exchanges",
                exchange);
        return spec;
    }

    private RMQConnection producerConnection(final ExchangeSpec spec) {
        return connectionRegistry.createOrGet(NamingUtils.producerConnectionName(spec.getProducer()));
    }

    private void checkNotStarted() {
        Preconditions.checkState(state != State.STOPPED, "Messaging is stopped and can't be restarted");
        Preconditions.checkState(state == State.NEW,
                "Publishers and handlers must be registered before messaging starts");
    }
}
