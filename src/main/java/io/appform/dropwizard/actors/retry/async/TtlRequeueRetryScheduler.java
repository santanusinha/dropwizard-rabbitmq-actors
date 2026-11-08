package io.appform.dropwizard.actors.retry.async;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.QueueType;
import io.appform.dropwizard.actors.actor.QueueTypeVisitorImpl;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.PublishingChannel;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.retry.RetryPolicy;
import io.appform.dropwizard.actors.retry.config.TtlRequeueConfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TtlRequeueRetryScheduler implements AsyncRetryScheduler {

    static final String DELAY_HEADER = "x-delay";

    private int maxRetryQueues = 10;
    private int maxPlannedAttempts = 10;
    private IsolationScope isolationScope = IsolationScope.EXCHANGE;

    private final String exchange;
    private final String queuePrefix;
    private final ActorConfig actorConfig;
    private final RMQConnection connection;
    private final PublishingChannel publisher;
    private final RetryPolicy<Boolean> retryPolicy;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TtlRequeueRetryScheduler(@NonNull RMQConnection connection, @NonNull RetryPolicy<Boolean> retryPolicy,
        @NonNull ActorConfig actorConfig, @NonNull String exchange, String queueName, @Nullable TtlRequeueConfig config,
        @NonNull PublishingChannel publisher) {
        this.exchange = exchange;
        this.publisher = publisher;
        this.connection = connection;
        this.retryPolicy = retryPolicy;
        this.actorConfig = actorConfig;
        if (config != null) {
            this.isolationScope = Objects.requireNonNullElse(config.getIsolationScope(), isolationScope);
            this.maxRetryQueues = Objects.requireNonNullElse(config.getMaxRetryQueues(), maxRetryQueues);
            this.maxPlannedAttempts = Objects.requireNonNullElse(config.getMaxPlannedAttempts(), maxPlannedAttempts);
        }
        var queueType = actorConfig.getQueueType().name().toLowerCase(Locale.ROOT);
        this.queuePrefix = getQueuePrefix(isolationScope == IsolationScope.QUEUE ? queueName : exchange, queueType);
    }

    @Override
    public synchronized void start() throws Exception {
        if (running.get()) {
            return;
        }
        var delays = AsyncRetryer.getPossibleDelays(retryPolicy, maxPlannedAttempts, true);
        if (delays.size() > maxRetryQueues) {
            throw new RetrySchedulingRejectedException("Retry policy needs " + delays.size()
                    + " retry queues, more than the allowed " + maxRetryQueues);
        }
        publisher.start();
        declareTopology(delays);
        running.set(true);
    }

    @Override
    public synchronized void schedule(String queueName, byte[] body, AMQP.BasicProperties properties,
                                       RetryState nextState, long delayMs) throws Exception {
        if (!running.get()) {
            throw new RetrySchedulingException("Retry scheduler is not running", null);
        }
        try {
            publisher.publish(waitingQueue(delayMs), waitingQueue(delayMs),
                    queueName, mergedProperties(properties, queueName, nextState).builder().expiration(null).build(),
                    body);
        } catch (Exception error) {
            throw new RetrySchedulingException("TTL Retry Requeue failed", error);
        }
        log.debug("Async retry published: queue: {}, delayMs: {}", queueName, delayMs);
    }

    @Override
    public synchronized void stop() throws Exception {
        running.set(false);
        publisher.close();
    }

    protected String waitingQueue(long delayMs) {
        var name = queuePrefix + "_" + delayMs;
        // actor queue names already carry the namespace
        return isolationScope == IsolationScope.QUEUE ? name : NamingUtils.prefixWithNamespace(name);
    }

    private void declareTopology(Collection<Long> delays) throws Exception {
        try (var topology = connection.newChannel()) {
            for (var delay : delays) {
                var waitingQueue = waitingQueue(delay);
                var arguments = new HashMap<String, Object>();
                arguments.put("x-message-ttl", delay);
                arguments.put(QueueType.X_QUEUE_TYPE, actorConfig.getQueueType()
                        .handleConfig(new QueueTypeVisitorImpl(actorConfig)).get(QueueType.X_QUEUE_TYPE));
                arguments.put("x-dead-letter-exchange", exchange);
                topology.exchangeDeclare(waitingQueue, "fanout", true);
                connection.ensure(topology, waitingQueue, "", waitingQueue, arguments);
                log.info("Declared async retry waiting queue: {}, delayMs: {}, dead-letters to: {}",
                    waitingQueue, delay, exchange);
            }
        }
    }

    private static AMQP.BasicProperties mergedProperties(AMQP.BasicProperties properties,
                                                          String queueName, RetryState nextState) {
        Map<String, Object> merged = new HashMap<>();
        if (properties.getHeaders() != null) {
            merged.putAll(properties.getHeaders());
        }
        merged.remove(DELAY_HEADER);
        merged.put(RETRY_QUEUE_HEADER, queueName);
        merged.put(RETRY_ATTEMPT_HEADER, nextState.attemptNumber());
        merged.put(RETRY_STARTED_AT_HEADER, nextState.startedAt());
        return properties.builder().headers(merged).build();
    }

    public static String getQueuePrefix(@NonNull String identifier, @NonNull String queueType) {
        return identifier + "_RETRY_" + queueType;
    }


    public enum IsolationScope {

        /**
         * One set of delay queues per exchange and queue type, shared by every queue bound to that exchange.
         */
        EXCHANGE,

        /**
         * One set of delay queues per actor queue, shared only by the shards of that queue.
         */
        QUEUE

    }

}
