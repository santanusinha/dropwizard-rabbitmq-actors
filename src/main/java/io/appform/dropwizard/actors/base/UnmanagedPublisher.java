package io.appform.dropwizard.actors.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.MessageProperties;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;

import java.io.IOException;
import java.util.Collections;

@Slf4j
public class UnmanagedPublisher<Message> {

    private final String name;
    private final ActorConfig config;
    private final RMQConnection connection;
    private final ObjectMapper mapper;
    private final String queueName;

    private Channel publishChannel;

    public UnmanagedPublisher(
            String name,
            ActorConfig config,
            RMQConnection connection,
            ObjectMapper mapper) {
        this.name = NamingUtils.prefixWithNamespace(name);
        this.config = config;
        this.connection = connection;
        this.mapper = mapper;
        this.queueName = NamingUtils.queueName(config.getPrefix(), name);
    }

    public final void publishWithDelay(Message message, long delayMilliseconds) throws Exception {
        log.info("Publishing message to exchange with delay: {}", delayMilliseconds);
        if (!config.isDelayed()) {
            log.warn("Publishing delayed message to non-delayed queue queue:{}", queueName);
        }

        if (config.getDelayType() == DelayType.TTL) {
            publishChannel.basicPublish(ttlExchange(config),
                    queueName,
                    new AMQP.BasicProperties.Builder()
                            .expiration(String.valueOf(delayMilliseconds))
                            .deliveryMode(2)
                            .build(),
                    mapper().writeValueAsBytes(message));
        } else {
            publish(message, new AMQP.BasicProperties.Builder()
                    .headers(Collections.singletonMap("x-delay", delayMilliseconds))
                    .deliveryMode(2)
                    .build());
        }
    }

    public final void publish(Message message) throws Exception {
        publish(message, MessageProperties.MINIMAL_PERSISTENT_BASIC);
    }

    public final void publish(Message message, AMQP.BasicProperties properties) throws Exception {
        String routingKey;
        if (config.isSharded()) {
            routingKey = NamingUtils.getShardedQueueName(queueName, getShardId());
        } else {
            routingKey = queueName;
        }
        publishChannel.basicPublish(config.getExchange(), routingKey, properties, mapper().writeValueAsBytes(message));
    }

    private final int getShardId() {
        return RandomUtils.nextInt(0, config.getShardCount());
    }

    public final long pendingMessagesCount() {
        try {
            if (config.isSharded()) {
                int messageCount  = 0 ;
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
            return publishChannel.messageCount(queueName + "_SIDELINE");
        } catch (IOException e) {
            log.error("Issue getting message count. Will return max", e);
        }
        return Long.MAX_VALUE;
    }


    public final boolean publishWithConfirmation(Message message, AMQP.BasicProperties properties, long timeout)
            throws Exception {
        publishChannel.confirmSelect();
        final CountDownLatch publishAckLatch = new CountDownLatch(1);
        publishChannel.addConfirmListener(new ConfirmListener() {
            public void handleNack(long deliveryTag, boolean multiple){
                publishAckLatch.countDown();
                System.out.println("Message : " + message + " not acknowledged");
            }

            public void handleAck(long deliveryTag, boolean multiple){
                publishAckLatch.countDown();
                System.out.println("Message : " + message + " acknowledged");
            }
        });

        String routingKey;
        if (config.isSharded()) {
            routingKey = NamingUtils.getShardedQueueName(queueName, getShardId());
        } else {
            routingKey = queueName;
        }
        publishChannel.basicPublish(config.getExchange(), routingKey, properties, mapper().writeValueAsBytes(message));

        if (!publishAckLatch.await(timeout, TimeUnit.MILLISECONDS)) {
            System.out.println("Timed out waiting for publish acks, Message : " + message);
            return false;
        }
        return true;
    }

    public final List<Message> publishWithConfirmation(List<Message> messages,  AMQP.BasicProperties properties, long timeout) throws Exception {
        publishChannel.confirmSelect();
        ConcurrentNavigableMap<Long, Message> outstandingConfirms = new ConcurrentSkipListMap<>();
        final CountDownLatch publishAckLatch = new CountDownLatch(messages.size());
        publishChannel.addConfirmListener(new ConfirmListener() {
            public void handleNack(long deliveryTag, boolean multiple) {
                publishAckLatch.countDown();
            }

            public void handleAck(long deliveryTag, boolean multiple) {
                if (multiple) {
                    ConcurrentNavigableMap<Long, Message> confirmed = outstandingConfirms.headMap(
                            deliveryTag, true
                    );
                    confirmed.clear();
                } else {
                    outstandingConfirms.remove(deliveryTag);
                }
                publishAckLatch.countDown();
            }
        });

        String routingKey;
        if (config.isSharded()) {
            routingKey = NamingUtils.getShardedQueueName(queueName, getShardId());
        } else {
            routingKey = queueName;
        }
        for (Message message : messages) {
            outstandingConfirms.put(publishChannel.getNextPublishSeqNo(), message);
            publishChannel.basicPublish(config.getExchange(), routingKey, properties,
                    mapper().writeValueAsBytes(message));
        }
        long startTime = System.nanoTime();

        if (!publishAckLatch.await(timeout, TimeUnit.MILLISECONDS)) {
            System.out.println("Timed out waiting for publish acks");
        }

        long endTime = System.nanoTime();
        System.out.format("Published %,d messages and handled confirms asynchronously in %,d ms%n", messages.size(),
                Duration.ofNanos(startTime - endTime).toMillis());
        return outstandingConfirms.values().stream().collect(Collectors.toList());
    }

    public void start() throws Exception {
        final String exchange = config.getExchange();
        final String dlx = config.getExchange() + "_SIDELINE";
        if (config.isDelayed()) {
            ensureDelayedExchange(exchange);
        } else {
            ensureExchange(exchange);
        }
        ensureExchange(dlx);

        this.publishChannel = connection.newChannel();
        connection.ensure(queueName + "_SIDELINE", queueName, dlx,
                connection.rmqOpts(config));
        if (config.isSharded()) {
            int bound = config.getShardCount();
            for (int shardId = 0; shardId < bound; shardId++) {
                connection.ensure(NamingUtils.getShardedQueueName(queueName, shardId), config.getExchange(),
                                  connection.rmqOpts(dlx, config));
            }
        } else {
            connection.ensure(queueName, config.getExchange(), connection.rmqOpts(dlx, config));
        }

        if (config.getDelayType() == DelayType.TTL) {
            connection.ensure(ttlQueue(queueName),
                    queueName,
                    ttlExchange(config),
                    connection.rmqOpts(exchange, config));
        }
    }

    private void ensureExchange(String exchange) throws IOException {
        connection.channel().exchangeDeclare(
                exchange,
                "direct",
                true,
                false,
                ImmutableMap.<String, Object>builder()
                        .put("x-ha-policy", "all")
                        .put("ha-mode", "all")
                        .build());
        log.info("Created exchange: {}", exchange);
    }

    private void ensureDelayedExchange(String exchange) throws IOException {
        if (config.getDelayType() == DelayType.TTL) {
            ensureExchange(ttlExchange(config));
        } else {
            connection.channel().exchangeDeclare(
                    exchange,
                    "x-delayed-message",
                    true,
                    false,
                    ImmutableMap.<String, Object>builder()
                            .put("x-ha-policy", "all")
                            .put("ha-mode", "all")
                            .put("x-delayed-type", "direct")
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
            publishChannel.close();
            log.info("Publisher channel closed for [{}] with prefix [{}]", name, config.getPrefix());
        } catch (Exception e) {
            log.error(String.format("Error closing publisher channel for [%s] with prefix [%s]", name, config.getPrefix()), e);
            throw e;
        }
    }

    protected final RMQConnection connection() {
        return connection;
    }

    protected final ObjectMapper mapper() {
        return mapper;
    }
}
