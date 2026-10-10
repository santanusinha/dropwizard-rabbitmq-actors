package io.appform.dropwizard.actors.retry.async;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.QueueTypeVisitorImpl;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fixed-TTL waiting queues dead-letter back to the exact consumed queue's routing key.
 * Retry progress is carried in x-actor-retry-* message headers so budgets survive
 * redeliveries; the headers are scoped to the queue that wrote them.
 * Confirms cover acceptance into the waiting queue, not its subsequent dead-letter transfer.
 */
@Slf4j
public final class TtlRequeueRetryScheduler implements AsyncRetryScheduler {

    static final String RETRY_QUEUE_HEADER = "x-actor-retry-queue";
    static final String RETRY_ATTEMPT_HEADER = "x-actor-retry-attempt";
    static final String RETRY_STARTED_AT_HEADER = "x-actor-retry-started-at";

    public record Context(RMQConnection connection, ActorConfig actorConfig, String exchange,
                          String queueName, Set<String> consumedQueues) {
        public Context {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(actorConfig, "actorConfig");
            Objects.requireNonNull(exchange, "exchange");
            Objects.requireNonNull(queueName, "queueName");
            consumedQueues = Set.copyOf(consumedQueues);
            if (consumedQueues.isEmpty()) {
                throw new IllegalArgumentException("Consumed queues must be nonempty");
            }
        }
    }

    private final Context context;
    private final long confirmTimeoutMs;
    private final int maxDelayLevels;
    private final Set<Long> declaredDelays = new HashSet<>();
    private final AtomicBoolean returned = new AtomicBoolean();
    private Channel publishChannel;
    private boolean running;

    public TtlRequeueRetryScheduler(Context context, long confirmTimeoutMs) {
        this(context, confirmTimeoutMs, 32);
    }

    public TtlRequeueRetryScheduler(Context context, long confirmTimeoutMs, int maxDelayLevels) {
        this.context = Objects.requireNonNull(context, "context");
        if (confirmTimeoutMs <= 0) {
            throw new IllegalArgumentException("Confirm timeout must be positive");
        }
        this.confirmTimeoutMs = confirmTimeoutMs;
        if (maxDelayLevels < 1) {
            throw new IllegalArgumentException("Maximum delay levels must be positive");
        }
        this.maxDelayLevels = maxDelayLevels;
    }

    @Override
    public synchronized void start() throws Exception {
        if (running) {
            throw new IllegalStateException("Retry scheduler is already running");
        }
        running = true;
    }

    @Override
    public RetryState restoreState(String queueName, AMQP.BasicProperties properties, long now) {
        Objects.requireNonNull(queueName, "queueName");
        var headers = properties == null || properties.getHeaders() == null
                ? Map.<String, Object>of() : properties.getHeaders();
        var retryQueue = headers.get(RETRY_QUEUE_HEADER);
        var attemptHeader = headers.get(RETRY_ATTEMPT_HEADER);
        var startedAtHeader = headers.get(RETRY_STARTED_AT_HEADER);
        if (retryQueue == null && attemptHeader == null && startedAtHeader == null) {
            return new RetryState(1, now);
        }
        if (retryQueue == null) {
            throw malformedMetadata("Retry metadata is missing the queue header");
        }
        if (!queueName.equals(retryQueue.toString())) {
            return new RetryState(1, now);
        }
        var attemptCount = headerNumber(attemptHeader);
        var startedAt = headerNumber(startedAtHeader);
        if (attemptCount == null || attemptCount <= 0 || attemptCount >= Integer.MAX_VALUE) {
            throw malformedMetadata("Attempt count must be a positive integer below " + Integer.MAX_VALUE);
        }
        if (startedAt == null || startedAt < 0 || startedAt > now) {
            throw malformedMetadata("Started-at must be a nonnegative timestamp not in the future");
        }
        return new RetryState(attemptCount, startedAt);
    }

    private static Long headerNumber(Object value) {
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private static IllegalStateException malformedMetadata(String detail) {
        return new IllegalStateException("Malformed actor retry metadata: " + detail);
    }

    private void ensureDelay(long delay) throws Exception {
        if (declaredDelays.contains(delay)) return;
        if (declaredDelays.size() >= maxDelayLevels) {
            throw new IllegalStateException("Retry scheduler exceeded its maximum delay levels");
        }
        var exchange = retryName(context.exchange(), delay);
        var queue = retryName(context.queueName(), delay);
        var arguments = new HashMap<>(context.actorConfig().getQueueType()
                .handleConfig(new QueueTypeVisitorImpl(context.actorConfig())));
        arguments.put("x-message-ttl", delay);
        arguments.put("x-dead-letter-exchange", context.exchange());
        try (var channel = context.connection().newChannel()) {
            channel.exchangeDeclare(exchange, "direct", true);
            channel.queueDeclare(queue, true, false, false, arguments);
            for (var consumedQueue : context.consumedQueues()) {
                channel.queueBind(queue, exchange, consumedQueue);
            }
        }
        declaredDelays.add(delay);
        log.info("Declared async retry delay topology: delayMs: {}, queue: {}, exchange: {}", delay, queue, exchange);
    }

    @Override
    public synchronized void schedule(String queueName, byte[] body, AMQP.BasicProperties properties,
                                      RetryState nextState, long delayMs) throws Exception {
        if (!running) {
            throw new IllegalStateException("Retry scheduler is not running");
        }
        if (!context.consumedQueues().contains(queueName) || delayMs < 0) {
            throw new IllegalArgumentException("Invalid retry destination or delay");
        }
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(nextState, "nextState");
        try {
            ensureDelay(delayMs);
            if (publishChannel == null || !publishChannel.isOpen()) {
                publishChannel = context.connection().newChannel();
                publishChannel.confirmSelect();
                publishChannel.addReturnListener(message -> returned.set(true));
            }
            returned.set(false);
            // Serialize publishes so a return/confirm can only belong to this handoff.
            publishChannel.basicPublish(retryName(context.exchange(), delayMs), queueName, true,
                    mergedProperties(properties, queueName, nextState).builder().expiration(null).build(), body);
            publishChannel.waitForConfirmsOrDie(confirmTimeoutMs);
            if (returned.get()) {
                throw new IOException("Retry message was returned as unroutable");
            }
            log.info("Performed ASYNC TTL Requeue for queueName :: {} :: with retry :: {}ms", queueName, delayMs);
        } catch (Exception error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            try {
                closeChannel();
            } catch (Exception closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        running = false;
        declaredDelays.clear();
        closeChannel();
    }

    private void closeChannel() throws Exception {
        var channel = publishChannel;
        publishChannel = null;
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    private static AMQP.BasicProperties mergedProperties(AMQP.BasicProperties properties,
                                                         String queueName, RetryState nextState) {
        Map<String, Object> merged = new HashMap<>();
        if (properties.getHeaders() != null) {
            merged.putAll(properties.getHeaders());
        }
        merged.put(RETRY_QUEUE_HEADER, queueName);
        merged.put(RETRY_ATTEMPT_HEADER, nextState.attemptNumber());
        merged.put(RETRY_STARTED_AT_HEADER, nextState.startedAt());
        return properties.builder().headers(merged).build();
    }

    public String retryName(String name, long delayMs) {
        return name + "_RETRY_" + delayMs;
    }
}
