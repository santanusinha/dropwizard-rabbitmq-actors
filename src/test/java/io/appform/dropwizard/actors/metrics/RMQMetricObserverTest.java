package io.appform.dropwizard.actors.metrics;

import com.codahale.metrics.MetricRegistry;
import io.appform.dropwizard.actors.common.RMQOperation;
import io.appform.dropwizard.actors.config.MetricConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RMQMetricObserverTest {

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
    void testExecuteWithNoException() {
        val context = PublishObserverContext.builder()
                .operation(RMQOperation.PUBLISH)
                .queueName("default")
                .build();
        assertEquals(terminate(), rmqMetricObserver.executePublish(context, this::terminate));
        val key = MetricKeyData.builder().operation(context.getOperation()).queueName(context.getQueueName()).build();
        validateMetrics(rmqMetricObserver.getMetricCache().get(key), 1, 0);
    }

    @Test
    void testExecuteWithException() {
        val context = PublishObserverContext.builder()
                .operation(RMQOperation.PUBLISH)
                .queueName("default")
                .build();
        assertThrows(RuntimeException.class, () -> rmqMetricObserver.executePublish(context, this::terminateWithException));
        val key = MetricKeyData.builder().operation(context.getOperation()).queueName(context.getQueueName()).build();
        validateMetrics(rmqMetricObserver.getMetricCache().get(key), 0, 1);
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
