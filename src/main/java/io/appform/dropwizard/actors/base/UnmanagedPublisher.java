package io.appform.dropwizard.actors.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.DelayType;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
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

    /**
     * @param messages : Messages to be published
     * @param properties
     * @param timeout : in MS timeout for waiting on countDownLatch
     * @param unit : timeout unit
     * @return : List of message nacked
     * @throws Exception
     */
    public List<Message> publishWithConfirmListener(List<Message> messages, AMQP.BasicProperties properties,
            long timeout, @NotNull TimeUnit unit) throws Exception {
        publishChannel.confirmSelect();
        ConcurrentNavigableMap<Long, Message> outstandingConfirms = new ConcurrentSkipListMap<>();
        List<Message> nackedMessages = new ArrayList<>();
        CountDownLatch publishAckLatch = new CountDownLatch(messages.size());

        publishChannel.addConfirmListener((sequenceNumber, multiple) -> {
            messagesAck(sequenceNumber, multiple, outstandingConfirms, publishAckLatch);
        }, (sequenceNumber, multiple) -> {
            nackedMessages.addAll(messagesNack(sequenceNumber, multiple, outstandingConfirms, publishAckLatch));
        });


        long startTime = System.nanoTime();

        for (Message message : messages) {
            try {
                String routingKey = NamingUtils.getRoutingKey(queueName, config);
                outstandingConfirms.put(publishChannel.getNextPublishSeqNo(), message);
                publishChannel.basicPublish(config.getExchange(), routingKey, properties,
                        mapper().writeValueAsBytes(message));
            } catch (Exception e) {
                log.error(String.format("Failed to publish Message : %s with exception %s", message, e));
                publishAckLatch.countDown();
            }
        }

        if (!publishAckLatch.await(timeout, unit)) {
            log.error("Timed out waiting for publish acks");
        }

        long endTime = System.nanoTime();

        log.info(String.format("Published %d messages with confirmListener in %d ms. Total Messages : %d", messages.size() - outstandingConfirms.size(),
                Duration.ofNanos(startTime - endTime).toMillis(), messages.size()));
        nackedMessages.addAll(outstandingConfirms.values());
        return nackedMessages;
    }


    private void messagesAck(long sequenceNumber, boolean multiple, ConcurrentNavigableMap<Long, Message> outstandingConfirms, CountDownLatch publishAckLatch)
    {
        if (multiple) {
            ConcurrentNavigableMap<Long, Message> confirmed = outstandingConfirms.headMap(
                    sequenceNumber, true
            );
            for(int i =0;i<confirmed.size();i++)
                publishAckLatch.countDown();
            confirmed.clear();
        } else {
            publishAckLatch.countDown();
            outstandingConfirms.remove(sequenceNumber);
        }
    }

    private List<Message> messagesNack(long sequenceNumber, boolean multiple, ConcurrentNavigableMap<Long, Message> outstandingConfirms, CountDownLatch publishAckLatch)
    {
        List<Message> nackedMessages = new ArrayList<>();
        if(multiple == true)
        {
            ConcurrentNavigableMap<Long, Message> nacked = outstandingConfirms.headMap(
                    sequenceNumber, true
            );
            for(int i =0;i<nacked.size();i++)
                publishAckLatch.countDown();
            nackedMessages.addAll(nacked.values());
            nacked.clear();
        }
        else
        {
            publishAckLatch.countDown();
            nackedMessages.add(outstandingConfirms.get(sequenceNumber));
            outstandingConfirms.remove(sequenceNumber);
        }
        return nackedMessages;
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

    public void setPublishChannel(Channel publishChannel){
        this.publishChannel = publishChannel;
    }
}
