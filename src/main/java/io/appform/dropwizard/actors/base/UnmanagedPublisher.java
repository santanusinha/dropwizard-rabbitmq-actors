package io.appform.dropwizard.actors.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.actor.ExchangeType;
import io.appform.dropwizard.actors.actor.ExchangeTypeBindingKeysVisitor;
import io.appform.dropwizard.actors.actor.ExchangeTypeVisitor;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.config.RmqManagementConfig;
import io.appform.dropwizard.actors.connectivity.BindingKeyMatcher;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.connectivity.RmqManagementClient;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import io.appform.dropwizard.actors.observers.RMQObserver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

@Slf4j
public class UnmanagedPublisher<Message> {

    @Getter
    private final String queueName;
    private final ActorConfig config;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final ShardIdCalculator<Message> shardIdCalculator;
    private final RoutingKeyResolver<Message> routingKeyResolver;
    private final RMQObserver observer;
    private Channel publishChannel;

    public UnmanagedPublisher(
            String name,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper) {
        this(name,
             config,
             new RandomShardIdCalculator<>(config),
             connection,
             mapper);
    }

    public UnmanagedPublisher(final String queueName,
                              final ActorConfig config,
                              final ShardIdCalculator<Message> shardIdCalculator,
                              final RMQConnection connection,
                              final ObjectMapper mapper) {
        this(queueName, config, shardIdCalculator, null, connection, mapper);
    }

    public UnmanagedPublisher(final String queueName,
                              final ActorConfig config,
                              final ShardIdCalculator<Message> shardIdCalculator,
                              final RoutingKeyResolver<Message> routingKeyResolver,
                              final RMQConnection connection,
                              final ObjectMapper mapper) {
        this.queueName = queueName;
        this.config = config;
        this.shardIdCalculator = shardIdCalculator;
        this.routingKeyResolver = routingKeyResolver;
        this.connection = connection;
        this.mapper = mapper;
        this.observer = connection.getRootObserver();
    }

    public final void publishWithDelay(final Message message, final long delayMilliseconds) throws Exception {
        log.info("Publishing message to exchange with delay: {}", delayMilliseconds);
        val properties = getPropertiesWithDelay(delayMilliseconds);
        publishWithDelay(message, properties);
    }

    private void publishWithDelay(final Message message, final AMQP.BasicProperties properties) throws Exception {
        if (!config.isDelayed()) {
            log.warn("Publishing delayed message to non-delayed queue queue:{}", queueName);
        }
        if (config.getDelayType() == DelayType.TTL) {
            val routingKey = getRoutingKey(message);
            val context = PublishObserverContext.builder()
                    .queueName(queueName)
                    .messageProperties(properties)
                    .build();
            observer.executePublish(context, messageDetails -> {
                try {
                    publishChannel.basicPublish(ttlExchange(config),
                            routingKey, messageDetails.getMessageProperties(),
                            mapper().writeValueAsBytes(message));
                } catch (IOException e) {
                    log.error("Error while publishing", e);
                    throw RabbitmqActorException.propagate(e);
                }
                return null;
            });
        } else {
            publish(message, properties);
        }
    }
    public final void publishWithExpiry(final Message message, final long expiryInMs) throws Exception {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .build();
        val finalProperties = getPropertiesWithExpiry(properties, expiryInMs);
        publish(message, finalProperties);
    }

    public final void publishWithDelayAndExpiry(final Message message,
                                                final long expiryInMs,
                                                final long delayMilliseconds) throws Exception {
        AMQP.BasicProperties properties = getPropertiesWithDelay(delayMilliseconds);
        val finalProperties = getPropertiesWithExpiry(properties, expiryInMs);
        publishWithDelay(message, finalProperties);

    }

    public final void publish(final Message message) throws Exception {
        publish(message, MessageProperties.MINIMAL_PERSISTENT_BASIC);
    }

    public final void publish(final Message message, final AMQP.BasicProperties properties) throws Exception {
        val routingKey = getRoutingKey(message);
        val context = PublishObserverContext.builder()
                .queueName(queueName)
                .messageProperties(properties)
                .build();
        observer.executePublish(context, messageDetails -> {
            val enrichedProperties = getEnrichedProperties(messageDetails.getMessageProperties());
            try {
                publishChannel.basicPublish(config.getExchange(), routingKey, enrichedProperties, mapper().writeValueAsBytes(message));
            } catch (IOException e) {
                log.error("Error while publishing", e);
                throw RabbitmqActorException.propagate(e);
            }
            return null;
        });
    }

    private AMQP.BasicProperties getEnrichedProperties(AMQP.BasicProperties properties) {
        HashMap<String, Object> enrichedHeaders = new HashMap<>();
        if (properties.getHeaders() != null) {
            enrichedHeaders.putAll(properties.getHeaders());
        }
        enrichedHeaders.put(Constants.MESSAGE_PUBLISHED_TEXT, Instant.now()
                .toEpochMilli());
        return properties.builder()
                .headers(Collections.unmodifiableMap(enrichedHeaders))
                .build();
    }

    private AMQP.BasicProperties getPropertiesWithDelay(final long delayMilliseconds){
        if(config.getDelayType() == DelayType.TTL){
            return new AMQP.BasicProperties.Builder()
                    .expiration(String.valueOf(delayMilliseconds))
                    .deliveryMode(2)
                    .build();
        }
            return new AMQP.BasicProperties.Builder()
                    .headers(Collections.singletonMap("x-delay", delayMilliseconds))
                    .deliveryMode(2)
                    .build();
    }

    private AMQP.BasicProperties getPropertiesWithExpiry(final AMQP.BasicProperties properties, final long expiryInMs){
        if (expiryInMs <= 0) {
            return properties;
        }
        val expiresAt = Instant.now()
                .toEpochMilli() + expiryInMs;
        return new AMQP.BasicProperties.Builder().headers(ImmutableMap.of(Constants.MESSAGE_EXPIRY_TEXT, expiresAt))
                .build();
    }

    public final long pendingMessagesCount() {
        try {
            if (config.isSharded()) {
                long messageCount  = 0 ;
                for (int i = 0; i < config.getShardCount(); i++) {
                    String shardedQueueName = NamingUtils.getShardedQueueName(queueName, i);
                    messageCount += publishChannel.messageCount(shardedQueueName);
                }
                return messageCount;
            }
            else {
                return publishChannel.messageCount(queueName);
            }
        } catch (IOException e) {
            log.error("Issue getting message count. Will return max", e);
        }
        return Long.MAX_VALUE;
    }

    public final long pendingSidelineMessagesCount() {
        try {
            return publishChannel.messageCount(NamingUtils.getSideline(queueName));
        } catch (IOException e) {
            log.error("Issue getting message count. Will return max", e);
        }
        return Long.MAX_VALUE;
    }

    public final long pendingSidelineProcessorMessagesCount() {
        try {
            return publishChannel.messageCount(NamingUtils.getSidelineProcessor(queueName));
        } catch (IOException e) {
            log.error("Issue getting message count. Will return max", e);
        }
        return Long.MAX_VALUE;
    }

    public void start() throws Exception {
        final String exchange = config.getExchange();
        final String dlx = NamingUtils.getSideline(config.getExchange());
        final ExchangeType exchangeType = getExchangeType();
        if (config.isDelayed()) {
            ensureDelayedExchange(exchange);
        } else {
            ensureExchange(exchange, exchangeType);
        }
        // Sideline/DLX is always DIRECT: dead-lettered messages route by exact routing key = queue name.
        // FANOUT would broadcast to all sideline queues; TOPIC's patterns wouldn't match the queue name.
        ensureExchange(dlx, ExchangeType.DIRECT);

        this.publishChannel = connection.newChannel();
        String sidelineQueueName = NamingUtils.getSideline(queueName);
        connection.ensure(sidelineQueueName, queueName, dlx, connection.rmqOpts(config));
        if (config.isSharded()) {
            // Sharding is only valid with a DIRECT exchange (TOPIC/FANOUT + sharding are rejected by
            // ActorConfig validation), so each shard queue is bound by its own name - unchanged historical path.
            int bound = config.getShardCount();
            for (int shardId = 0; shardId < bound; shardId++) {
                String shardedQueueName = NamingUtils.getShardedQueueName(queueName, shardId);
                connection.ensure(shardedQueueName, config.getExchange(), connection.rmqOpts(dlx, config));
                connection.addBinding(sidelineQueueName, dlx, shardedQueueName);
            }
        } else if (getExchangeType() == ExchangeType.TOPIC) {
            // TOPIC: bindingKeys are user-configurable and can change/shrink across deploys, so they are
            // susceptible to drift on durable queues. ensureTopicQueue enforces bindingKey immutability when
            // the Management API is enabled.
            ensureTopicQueue(queueName, dlx);
        } else if (getExchangeType() == ExchangeType.FANOUT) {
            // FANOUT: the binding key is a constant "" (not user-configurable), so bindingKey drift does not
            // apply. However, changing an actor's `exchange` leaves the queue bound to the OLD exchange too
            // (binds are additive, we never unbind), so it silently keeps receiving from the old fanout
            // exchange. ensureFanoutQueue detects that stale-exchange binding when the Management API is enabled.
            ensureFanoutQueue(queueName, dlx);
        } else {
            // DIRECT: unchanged from the historical behaviour - no Management-API checks. The binding key is the
            // queue name (not user-configurable), so there is no bindingKey drift. Exchange-level drift (a stale
            // binding left by changing the actor's exchange) is a pre-existing DIRECT behaviour and is
            // intentionally NOT checked here: long-lived DIRECT queues may legitimately carry legacy/manual
            // bindings, and failing their startup when the check is enabled would break existing deployments.
            connection.ensure(queueName, config.getExchange(), connection.rmqOpts(dlx, config));
        }

        if (config.getDelayType() == DelayType.TTL) {
            connection.ensure(ttlQueue(queueName),
                    queueName,
                    ttlExchange(config),
                    connection.rmqOpts(exchange, config));
        }

        if (config.isSidelineProcessorEnabled()) {
            final var sidelineProcessorExchange = NamingUtils.getSidelineProcessor(config.getExchange());
            // Always DIRECT: the sideline-processor queue is bound by its exact queue name, so we need
            // point-to-point routing regardless of the main exchange type (TOPIC patterns wouldn't match
            // the queue name and FANOUT would broadcast to every sideline-processor queue).
            ensureExchange(sidelineProcessorExchange, ExchangeType.DIRECT);

            final var sidelineProcessorQueue = NamingUtils.getSidelineProcessor(queueName);

            if(config.isSharded())
            {
                int bound = config.getShardCount();

                for (int shardId = 0; shardId < bound ; shardId++)
                {
                    String shardedQueueName = NamingUtils.getShardedQueueName(sidelineProcessorQueue, shardId);
                    connection.ensure(shardedQueueName, sidelineProcessorExchange,
                            dlqOpts(dlx, shardedQueueName));
                    connection.addBinding(sidelineQueueName, dlx, shardedQueueName);
                }
            }
            else
            {
                connection.ensure(sidelineProcessorQueue, sidelineProcessorExchange,
                        dlqOpts(dlx, sidelineProcessorQueue));
                connection.addBinding(sidelineQueueName, dlx, sidelineProcessorQueue);
            }
        }
    }

    private ExchangeType getExchangeType() {
        final ExchangeType exchangeType = config.getExchangeType();
        // TOPIC routes on a message-derived key, so a resolver is mandatory.
        if (exchangeType == ExchangeType.TOPIC && routingKeyResolver == null) {
            throw new IllegalStateException(String.format(
                    "A RoutingKeyResolver must be supplied for TOPIC exchange. queue: %s", queueName));
        }
        // TTL delay re-routes via the queue name, which only works on a DIRECT exchange (see ensureDelayedExchange).
        if (config.isDelayed() && config.getDelayType() == DelayType.TTL
                && exchangeType != ExchangeType.DIRECT) {
            throw new IllegalStateException(String.format(
                    "TTL based delay is only supported for a DIRECT exchange. queue: %s", queueName));
        }
        return exchangeType;
    }

    private List<String> mainExchangeBindingKeys(final String queue) {
        return config.getExchangeType()
                .handleConfig(new ExchangeTypeBindingKeysVisitor(config, queue));
    }

    /**
     * Declares and binds a TOPIC queue, treating {@code bindingKeys} as immutable per queue when the
     * Management API is enabled.
     *
     * <p>bindingKeys are additive at the AMQP level (there is no unbind here), so silently changing them on a
     * durable queue would leave stale bindings. When {@code managementConfig.enabled} is true we:
     * <ul>
     *   <li>read the current bindings via the Management API (AMQP cannot list bindings);</li>
     *   <li>if the queue already has bindings, require an exact match with the configured set and fail
     *       startup on mismatch <b>without</b> any bind/unbind;</li>
     *   <li>if the queue is new (first creation), bind the configured keys and re-read to detect a
     *       divergent concurrent creator (first-creation race), failing startup on mismatch.</li>
     * </ul>
     *
     * <p><b>The match is exact, so ANY change to an existing queue's bindingKeys fails startup</b> - not just
     * removals. This is deliberate: although a pure addition (e.g. {@code [A] -> [A, B]}) would be safe on its
     * own (an extra {@code queueBind} leaves no stale binding), we keep a single, simple invariant - "the
     * broker's bindings for a queue always equal its configured bindingKeys" - rather than a subtler
     * "additions allowed, removals not" contract. Removals/replacements are genuinely unsafe (they would leave
     * a stale binding, since we never unbind). To change bindingKeys on a live queue, use a new/versioned queue
     * (or perform the bind/unbind out-of-band).
     *
     * <p>When the Management API is disabled, this falls back to the historical additive bind.
     */
    private void ensureTopicQueue(final String queue, final String dlx) throws Exception {
        // Exchange-level drift applies to TOPIC too (independent of bindingKeys): changing the actor's
        // exchange would leave a stale binding to the old exchange. Verify BEFORE binding so a changed
        // exchange fails without first creating the new binding.
        verifyNoStaleExchangeBindings(queue);

        final List<String> configuredKeys = mainExchangeBindingKeys(queue);
        final RmqManagementConfig mgmt = managementConfig();

        if (mgmt == null || !mgmt.isEnabled()) {
            // Feature off: preserve historical additive behaviour.
            bindAdditively(queue, dlx, configuredKeys);
            return;
        }

        final RmqManagementClient client = new RmqManagementClient(connection.getConfig(), mapper());
        final BindingReadResult read = tryReadBindings(client, queue, mgmt, "read");
        if (read.isFallbackToAdditiveBind()) {
            // Non-strict + API unavailable: skip the immutability check.
            bindAdditively(queue, dlx, configuredKeys);
            return;
        }

        if (isFirstCreation(read.getBindings())) {
            handleFirstCreation(client, queue, dlx, configuredKeys, mgmt);
        } else {
            handleExistingQueue(queue, dlx, configuredKeys, read.getBindings().get());
        }
    }

    /**
     * First creation: no bindings exist yet. Bind the configured keys, then re-read to detect a divergent
     * concurrent creator (the first-creation race) and fail startup on mismatch.
     */
    private void handleFirstCreation(final RmqManagementClient client,
                                     final String queue,
                                     final String dlx,
                                     final List<String> configuredKeys,
                                     final RmqManagementConfig mgmt) throws Exception {
        bindAdditively(queue, dlx, configuredKeys);

        final BindingReadResult reread = tryReadBindings(client, queue, mgmt, "re-read");
        if (reread.isFallbackToAdditiveBind() || reread.getBindings().isEmpty()) {
            // Non-strict + API unavailable on re-read: cannot verify the race; leave the bind as-is.
            return;
        }
        assertBindingKeysMatch(configuredKeys, reread.getBindings().get(), String.format(
                "bindingKeys mismatch after first-creation bind for queue '%s' on exchange '%s' "
                        + "(likely a concurrent creator with divergent bindingKeys).",
                queue, config.getExchange()));
    }

    /**
     * Established queue: bindingKeys are immutable. Require an exact match and never bind/unbind; only
     * (idempotently) ensure the queue declaration.
     */
    private void handleExistingQueue(final String queue,
                                     final String dlx,
                                     final List<String> configuredKeys,
                                     final List<String> existingKeys) throws Exception {
        assertBindingKeysMatch(configuredKeys, existingKeys, String.format(
                "bindingKeys are immutable for an existing queue. Mismatch for queue '%s' on exchange '%s'. "
                        + "To change bindingKeys, use a new/versioned queue.",
                queue, config.getExchange()));
        // Exact match: ensure the queue itself is declared (idempotent) without touching bindings.
        connection.ensureQueueOnly(queue, dlqOpts(dlx, queue));
    }

    /**
     * Declares and binds a FANOUT queue, guarding against a stale-exchange binding when the Management API is
     * enabled.
     *
     * <p>FANOUT's binding key is always the constant {@code ""}, so there is no bindingKey drift. The drift
     * that DOES apply is at the exchange level: if an actor's {@code exchange} is changed, the queue is bound
     * to the new exchange on redeploy but its binding to the OLD exchange is never removed (binds are
     * additive). It would then silently keep receiving from the old fanout exchange. So before binding we
     * verify the queue is bound to exactly the configured exchange; any other source exchange fails startup.
     *
     * <p>When the Management API is disabled, this falls back to the historical additive bind.
     */
    private void ensureFanoutQueue(final String queue, final String dlx) throws Exception {
        // Verify BEFORE binding so a changed exchange fails without first creating the new binding.
        verifyNoStaleExchangeBindings(queue);
        bindAdditively(queue, dlx, mainExchangeBindingKeys(queue));
    }

    /**
     * Verifies (when the Management API is enabled) that {@code queue} is bound to exactly its configured
     * exchange, i.e. it carries no binding to a different ("stale") exchange left over from changing the
     * actor's {@code exchange}. Because binds are additive and the framework never unbinds, such a stale
     * binding would silently keep delivering from the old exchange. On detection this fails startup with
     * {@link IllegalStateException} <b>without</b> any unbind. The implicit default (nameless) exchange binding
     * every queue has is ignored.
     *
     * <p>Called <b>before</b> binding the queue to its configured exchange, so a changed exchange fails fast
     * without first creating the new binding. On first creation the queue does not exist yet, so the read
     * returns no bindings and the check passes. Applied to TOPIC and FANOUT queues only; DIRECT is deliberately
     * excluded to keep its historical behaviour (legacy DIRECT queues may carry old/manual bindings). No-op
     * when the Management API is disabled.
     */
    private void verifyNoStaleExchangeBindings(final String queue) {
        final RmqManagementConfig mgmt = managementConfig();
        if (mgmt == null || !mgmt.isEnabled()) {
            return;
        }

        final RmqManagementClient client = new RmqManagementClient(connection.getConfig(), mapper());
        final Optional<Set<String>> sourceExchanges;
        try {
            sourceExchanges = client.fetchSourceExchanges(vhost(), queue);
        } catch (RuntimeException e) {
            if (mgmt.isFailOnManagementUnavailable()) {
                throw new IllegalStateException(String.format(
                        "Unable to read bindings from RabbitMQ Management API for queue '%s' (strict mode). "
                                + "Ensure the management plugin is enabled and reachable.", queue), e);
            }
            log.warn("Management API unavailable for queue {}; proceeding without stale-exchange check", queue, e);
            return;
        }

        if (sourceExchanges.isEmpty()) {
            // Queue disappeared between bind and read; nothing to verify.
            return;
        }

        final Set<String> staleExchanges = new HashSet<>(sourceExchanges.get());
        staleExchanges.remove(config.getExchange());
        if (!staleExchanges.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "Queue '%s' is bound to unexpected exchange(s) %s besides its configured exchange '%s'. "
                            + "This is a stale binding left by changing the actor's exchange."
                            + "To change the exchange, use a new/versioned queue.",
                    queue, staleExchanges, config.getExchange()));
        }
    }

    private RmqManagementConfig managementConfig() {
        return connection.getConfig() == null ? null : connection.getConfig().getManagementConfig();
    }

    private String vhost() {
        final String configured = connection.getConfig().getVirtualHost();
        return configured == null ? "/" : configured;
    }

    private void bindAdditively(final String queue, final String dlx, final List<String> configuredKeys)
            throws Exception {
        connection.ensureWithBindingKeys(queue, config.getExchange(), configuredKeys, dlqOpts(dlx, queue));
    }

    private boolean isFirstCreation(final Optional<List<String>> existing) {
        return existing.isEmpty() || BindingKeyMatcher.normalize(existing.get()).isEmpty();
    }

    /**
     * Reads the queue's bindings, applying the strict/warn policy on failure.
     *
     * @return a result whose {@code isFallbackToAdditiveBind()} is true only when the API was
     *         unavailable and {@code failOnManagementUnavailable} is false; otherwise it carries the
     *         (possibly empty, i.e. 404/first-creation) binding set. Strict failures throw.
     */
    private BindingReadResult tryReadBindings(final RmqManagementClient client,
                                              final String queue,
                                              final RmqManagementConfig mgmt,
                                              final String phase) {
        try {
            return BindingReadResult.of(client.fetchBindingKeys(vhost(), config.getExchange(), queue));
        } catch (RuntimeException e) {
            if (mgmt.isFailOnManagementUnavailable()) {
                throw new IllegalStateException(String.format(
                        "Unable to %s bindings from RabbitMQ Management API for queue '%s' (strict mode). "
                                + "Ensure the management plugin is enabled and reachable.", phase, queue), e);
            }
            log.warn("Management API unavailable on {} for queue {}; proceeding without immutable "
                    + "bindingKeys check", phase, queue, e);
            return BindingReadResult.fallback();
        }
    }

    private void assertBindingKeysMatch(final List<String> configuredKeys,
                                        final List<String> actualKeys,
                                        final String reason) {
        if (!BindingKeyMatcher.matches(configuredKeys, actualKeys)) {
            throw new IllegalStateException(String.format("%s configured=%s, actual=%s",
                    reason, BindingKeyMatcher.normalize(configuredKeys),
                    BindingKeyMatcher.normalize(actualKeys)));
        }
    }

    /**
     * Result of a binding read: either the (possibly empty) binding set, or a signal that the read failed
     * in non-strict mode and the caller should fall back to the historical additive bind.
     */
    @Value
    private static class BindingReadResult {
        boolean fallbackToAdditiveBind;
        Optional<List<String>> bindings;

        static BindingReadResult of(final Optional<List<String>> bindings) {
            return new BindingReadResult(false, bindings);
        }

        static BindingReadResult fallback() {
            return new BindingReadResult(true, Optional.empty());
        }
    }

    /**
     * Builds the queue arguments for a dead-lettered queue.
     *
     * <p>For a DIRECT exchange we deliberately do NOT set {@code x-dead-letter-routing-key}, preserving the
     * historical behaviour (RabbitMQ reuses the original routing key, which equals the queue name). This keeps
     * queue arguments byte-identical to previous versions and avoids PRECONDITION_FAILED errors when redeclaring
     * pre-existing queues on upgrade.
     *
     * <p>For TOPIC/FANOUT exchanges the original routing key would not match the direct {@code _SIDELINE} binding,
     * so we pin {@code x-dead-letter-routing-key} to the queue name to ensure dead-lettered messages reach the
     * correct sideline queue. These are new queues, so there is no existing-arg conflict.
     */
    private Map<String, Object> dlqOpts(final String deadLetterExchange,
                                        final String deadLetterRoutingKey) {
        if (config.getExchangeType() == ExchangeType.DIRECT) {
            return connection.rmqOpts(deadLetterExchange, config);
        }
        return connection.rmqOpts(deadLetterExchange, deadLetterRoutingKey, config);
    }

    private void ensureExchange(String exchange, ExchangeType exchangeType) throws IOException {
        connection.channel().exchangeDeclare(
                exchange,
                exchangeType.getAmqpType(), true);
        log.info("Created exchange: {} of type: {}", exchange, exchangeType.getAmqpType());
    }

    private void ensureDelayedExchange(String exchange) throws IOException {
        if (config.getDelayType() == DelayType.TTL) {
            // TTL delay is guarded to DIRECT only (see getExchangeType), so both exchanges are DIRECT.
            ensureExchange(ttlExchange(config), ExchangeType.DIRECT);
            ensureExchange(exchange, ExchangeType.DIRECT);
        } else {
            // https://blog.rabbitmq.com/posts/2015/04/scheduling-messages-with-rabbitmq/
            connection.channel().exchangeDeclare(
                    exchange,
                    "x-delayed-message",
                    true,
                    false,
                    ImmutableMap.<String, Object>builder()
                            // Delayed exchange forwards using the configured type once the delay elapses.
                            .put("x-delayed-type", config.getExchangeType().getAmqpType())
                            .build());
            log.info("Created delayed exchange: {}", exchange);
        }
    }

    private String ttlExchange(ActorConfig actorConfig) {
        return String.format("%s_TTL", actorConfig.getExchange());
    }

    private String ttlQueue(String queueName) {
        return String.format("%s_TTL", queueName);
    }

    public void stop() throws Exception {
        try {
            if (publishChannel.isOpen()) {
                publishChannel.close();
                log.info("Publisher channel closed for queue [{}]", queueName);
            } else {
                log.warn("Publisher channel already closed for queue [{}]", queueName);
            }
        } catch (Exception e) {
            log.error("Error closing publisher channel for queue [{}]", queueName, e);
            throw e;
        }
    }

    protected final RMQConnection connection() {
        return connection;
    }

    protected final ObjectMapper mapper() {
        return mapper;
    }

    private String getRoutingKey(Message message) {
        // Routing key depends on exchange type: DIRECT -> queue/shard name, TOPIC -> resolver, FANOUT -> ignored.
        return config.getExchangeType().handleConfig(new ExchangeTypeVisitor<String>() {
            @Override
            public String visitDirect() {
                return config.isSharded()
                       ? NamingUtils.getShardedQueueName(queueName, shardIdCalculator.calculateShardId(message))
                       : queueName;
            }

            @Override
            public String visitTopic() {
                return routingKeyResolver.resolve(message);
            }

            @Override
            public String visitFanout() {
                return "";
            }
        });
    }

}
