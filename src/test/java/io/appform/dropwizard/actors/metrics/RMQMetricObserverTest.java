package io.appform.dropwizard.actors.metrics;

import com.codahale.metrics.MetricRegistry;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.config.MetricConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.observers.ConsumeMessageDetails;
import io.appform.dropwizard.actors.observers.ConsumeObserverContext;
import io.appform.dropwizard.actors.observers.PublishMessageDetails;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RMQMetricObserverTest {
    private static final String PUBLISH = "publish";
    private static final String CONSUME = "consume";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_VALUE = "test-value";

    private RMQMetricObserver rmqMetricObserver;
    private RMQConfig config;
    private final MetricRegistry metricRegistry = new MetricRegistry();

    @BeforeEach
    public void setup() {
        this.config = RMQConfig.builder()
                .brokers(new ArrayList<>())
                .userName("")
                .threadPoolSize(1)
                .password("")
                .secure(false)
                .startupGracePeriodSeconds(1)
                .metricConfig(MetricConfig.builder().enabledForAll(true).build())
                .build();
        this.rmqMetricObserver = new RMQMetricObserver(config, metricRegistry);
    }

    @Test
    void testExecuteWhenMetricNotApplicable() {
        val properties = new AMQP.BasicProperties().builder().headers(Map.of(TEST_KEY, TEST_VALUE)).build();
        val context = PublishObserverContext.builder()
                .queueName("default")
                .messageProperties(properties)
                .build();
        val publishDetails = PublishMessageDetails.builder().messageProperties(properties).build();
        val config = this.config;
        config.setMetricConfig(MetricConfig.builder().enabledForAll(false).build());
        val publishMetricObserver = new RMQMetricObserver(config, metricRegistry);
        assertEquals(terminate(publishDetails),
                publishMetricObserver.executePublish(context, this::terminate));
    }

    @Test
    void testExecuteWithNoExceptionForPublish() {
        val properties = new AMQP.BasicProperties().builder().headers(Map.of(TEST_KEY, TEST_VALUE)).build();
        val context = PublishObserverContext.builder()
                .queueName("default")
                .messageProperties(properties)
                .build();
        val publishDetails = PublishMessageDetails.builder().messageProperties(properties).build();
        assertEquals(terminate(publishDetails), rmqMetricObserver.executePublish(context, this::terminate));
        val key = MetricKeyData.builder().operation(PUBLISH).queueName(context.getQueueName()).build();
        validateMetrics(rmqMetricObserver.getMetricCache().get(key), 1, 0);
    }

    @Test
    void testExecuteWithException() {
        val context = PublishObserverContext.builder()
                .queueName("default")
                .build();
        assertThrows(RuntimeException.class, () -> rmqMetricObserver.executePublish(context, this::terminateWithException));
        val key = MetricKeyData.builder().operation(PUBLISH).queueName(context.getQueueName()).build();
        validateMetrics(rmqMetricObserver.getMetricCache().get(key), 0, 1);
    }

    @Test
    void testExecuteForConsumeWithoutRedelivery() {
        val messageMetadata = new MessageMetadata(false, 1000, Map.of(TEST_KEY, TEST_VALUE));
        val context = ConsumeObserverContext.builder()
                .queueName("default")
                .messageMetadata(messageMetadata)
                .build();
        val consumeDetails = ConsumeMessageDetails.builder().messageMetadata(messageMetadata).build();
        assertEquals(terminate(consumeDetails), rmqMetricObserver.executeConsume(context, this::terminate));

        val key = MetricKeyData.builder()
                .operation(CONSUME)
                .queueName(context.getQueueName()).build();
        validateMetrics(rmqMetricObserver.getMetricCache().get(key), 1, 0);

        val redeliveryKey = MetricKeyData.builder()
                .operation(CONSUME)
                .queueName(context.getQueueName())
                .redelivered(true)
                .build();
        assertNull(rmqMetricObserver.getMetricCache().get(redeliveryKey));
    }

    @Test
    void testExecuteForConsumeWithRedelivery() {
        val messageMetadata = new MessageMetadata(false, 1000, Map.of(TEST_KEY, TEST_VALUE));
        val context = ConsumeObserverContext.builder()
                .queueName("default")
                .messageMetadata(messageMetadata)
                .redelivered(true)
                .build();
        val consumeDetails = ConsumeMessageDetails.builder().messageMetadata(messageMetadata).build();
        assertEquals(terminate(consumeDetails), rmqMetricObserver.executeConsume(context, this::terminate));

        val key = MetricKeyData.builder()
                .operation(CONSUME)
                .queueName(context.getQueueName()).build();
        validateMetrics(rmqMetricObserver.getMetricCache().get(key), 1, 0);

        val redeliveryKey = MetricKeyData.builder()
                .operation(CONSUME)
                .queueName(context.getQueueName())
                .redelivered(context.isRedelivered())
                .build();
        validateMetrics(rmqMetricObserver.getMetricCache().get(redeliveryKey), 1, 0);
    }

    private void validateMetrics(final MetricData metricData,
                                 final int successCount,
                                 final int failedCount) {
        assertEquals(1, metricData.getTotal().getCount());
        assertEquals(1, metricData.getTimer().getCount());
        assertEquals(successCount, metricData.getSuccess().getCount());
        assertEquals(failedCount, metricData.getFailed().getCount());
    }

    private String terminate(PublishMessageDetails publishMessageDetails) {
        return publishMessageDetails.getMessageProperties().getHeaders().get(TEST_KEY).toString();
    }
    private String terminate(ConsumeMessageDetails consumeMessageDetails) {
        return consumeMessageDetails.getMessageMetadata().getHeaders().get(TEST_KEY).toString();
    }

    private Integer terminateWithException(PublishMessageDetails properties) {
        throw new RuntimeException();
    }
}
