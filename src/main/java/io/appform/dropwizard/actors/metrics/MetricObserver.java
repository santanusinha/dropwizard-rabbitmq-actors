package io.appform.dropwizard.actors.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.codahale.metrics.Timer;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.observers.ObserverContext;
import io.appform.dropwizard.actors.observers.RMQPublishObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


@Slf4j
public class MetricObserver extends RMQPublishObserver {
    private final RMQConfig rmqConfig;
    private final MetricRegistry metricRegistry;

    @Getter
    private final Map<MetricKeyData, MetricData> metricCache = new ConcurrentHashMap<>();

    public MetricObserver(final RMQConfig rmqConfig,
                          final MetricRegistry metricRegistry) {
        this.rmqConfig = rmqConfig;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public <T> T execute(ObserverContext context, Supplier<T> supplier) {
        if (!rmqConfig.isMetricsEnabled()) {
            return proceed(context, supplier);
        }
        val metricData = getMetricData(context);
        metricData.getTotal().mark();
        val timer = metricData.getTimer().time();
        try {
            val response = proceed(context, supplier);
            metricData.getSuccess().mark();
            return response;
        } catch (Throwable t) {
            metricData.getFailed().mark();
            throw t;
        } finally {
            timer.stop();
        }
    }

    private MetricData getMetricData(final ObserverContext context) {
        val metricKeyData = MetricKeyData.builder()
                .queueName(context.getQueueName())
                .operation(context.getOperation())
                .build();
        return metricCache.computeIfAbsent(metricKeyData, key ->
                getMetricData(MetricUtil.getMetricPrefix(metricKeyData)));
    }

    private MetricData getMetricData(final String metricPrefix) {
        return MetricData.builder()
                .timer(metricRegistry.timer(MetricRegistry.name(metricPrefix, "latency"),
                        () -> new Timer(new SlidingTimeWindowArrayReservoir(60, TimeUnit.SECONDS))))
                .success(metricRegistry.meter(MetricRegistry.name(metricPrefix, "success")))
                .failed(metricRegistry.meter(MetricRegistry.name(metricPrefix, "failed")))
                .total(metricRegistry.meter(MetricRegistry.name(metricPrefix, "total")))
                .build();
    }
}
