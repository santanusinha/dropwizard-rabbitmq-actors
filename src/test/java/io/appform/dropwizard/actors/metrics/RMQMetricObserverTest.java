package io.appform.dropwizard.actors.metrics;

import com.codahale.metrics.MetricRegistry;
import io.appform.dropwizard.actors.common.RMQOperation;
import io.appform.dropwizard.actors.config.MetricConfig;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.observers.ObserverContext;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RMQMetricObserverTest {

    private RMQMetricObserver publishMetricObserver;
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
        this.publishMetricObserver = new RMQMetricObserver(config, metricRegistry);
    }

    @Test
    void testExecuteWhenMetricNotApplicable() {
        val config = this.config;
        config.setMetricConfig(MetricConfig.builder().enabledForAll(false).build());
        val publishMetricObserver = new RMQMetricObserver(config, metricRegistry);
        assertEquals(terminate(),
                publishMetricObserver.executePublish(ObserverContext.builder().build(), this::terminate));
    }

    @Test
    void testExecuteWithNoException() {
        val context = ObserverContext.builder()
                .operation(RMQOperation.PUBLISH)
                .queueName("default")
                .build();
        assertEquals(terminate(), publishMetricObserver.executePublish(context, this::terminate));
        val key = MetricKeyData.builder().operation(context.getOperation()).queueName(context.getQueueName()).build();
        validateMetrics(publishMetricObserver.getMetricCache().get(key), 1, 0);
    }

    @Test
    void testExecuteWithException() {
        val context = ObserverContext.builder()
                .operation(RMQOperation.PUBLISH)
                .queueName("default")
                .build();
        assertThrows(RuntimeException.class, () -> publishMetricObserver.executePublish(context, this::terminateWithException));
        val key = MetricKeyData.builder().operation(context.getOperation()).queueName(context.getQueueName()).build();
        validateMetrics(publishMetricObserver.getMetricCache().get(key), 0, 1);
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
