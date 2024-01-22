package io.appform.dropwizard.actors.metrics;

import com.codahale.metrics.MetricRegistry;
import io.appform.dropwizard.actors.config.MetricConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.observers.ConsumeObserverContext;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RMQMetricObserverTest {
    private static final String PUBLISH = "publish";
    private static final String CONSUME = "consume";

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
        val config = this.config;
        config.setMetricConfig(MetricConfig.builder().enabledForAll(false).build());
        val publishMetricObserver = new RMQMetricObserver(config, metricRegistry);
        assertEquals(terminate(),
                publishMetricObserver.executePublish(PublishObserverContext.builder().build(), this::terminate));
    }

    @Test
    void testExecuteWithNoExceptionForPublish() {
        val context = PublishObserverContext.builder()
                .queueName("default")
                .build();
        assertEquals(terminate(), rmqMetricObserver.executePublish(context, this::terminate));
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
        val context = ConsumeObserverContext.builder()
                .queueName("default")
                .build();
        assertEquals(terminate(), rmqMetricObserver.executeConsume(context, this::terminate));

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
        val context = ConsumeObserverContext.builder()
                .queueName("default")
                .redelivered(true)
                .build();
        assertEquals(terminate(), rmqMetricObserver.executeConsume(context, this::terminate));

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

    private Integer terminate() {
        return 1;
    }

    private Integer terminateWithException() {
        throw new RuntimeException();
    }
}
