package io.dropwizard.actors.connectivity;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.StandardMetricsCollector;
import io.dropwizard.actors.config.RMQConfig;
import io.dropwizard.lifecycle.Managed;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class RMQConnection implements Managed {

    @Getter
    private final RMQConfig config;
    private MetricRegistry metricRegistry;
    @VisibleForTesting
    @Getter
    private Connection connection;
    private Channel channel;
    private ExecutorService executorService;

    public RMQConnection(RMQConfig config, MetricRegistry metricRegistry) {
        this.config = config;
        this.metricRegistry = metricRegistry;
    }


    @Override
    public void start() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setMetricsCollector(new StandardMetricsCollector(metricRegistry));
        if(config.isSecure()) {
            factory.setUsername(config.getUserName());
            factory.setPassword(config.getPassword());
            if(Strings.isNullOrEmpty(config.getCertStorePath())) {
                factory.useSslProtocol();
            }
            else {
                Preconditions.checkNotNull(config.getCertPassword(), "Cert password is required if cert file path has been provided");
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(new FileInputStream(config.getCertStorePath()), config.getCertPassword().toCharArray());

                KeyStore tks = KeyStore.getInstance("JKS");
                tks.load(new FileInputStream(config.getServerCertStorePath()), config.getServerCertPassword().toCharArray());
                SSLContext c = SSLContexts.custom()
                        .useProtocol("TLSv1.2")
                        .loadTrustMaterial(tks, new TrustSelfSignedStrategy())
                        .loadKeyMaterial(ks, config.getCertPassword().toCharArray(), (aliases, socket) -> "clientcert")
                        .build();
                factory.useSslProtocol(c);
                factory.setVirtualHost(config.getUserName());
            }
        }
        else {
            factory.setUsername(config.getUserName());
            factory.setPassword(config.getPassword());
        }
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(3000);
        factory.setRequestedHeartbeat(60);
        this.executorService = new InstrumentedExecutorService(Executors.newFixedThreadPool(config.getThreadPoolSize()),
                metricRegistry, "rabbitmq-actors");
        connection = factory.newConnection(executorService, config.getBrokers().stream()
                .map(broker -> new Address(broker.getHost())).toArray(Address[]::new));
        channel = connection.createChannel();
    }

    public void ensure(final String queueName,
                       final String exchange) throws Exception {
        ensure(queueName, queueName, exchange, rmqOpts());
    }

    public void ensure(final String queueName,
                       final String exchange,
                       final Map<String, Object> rmqOpts) throws Exception {
        ensure(queueName, queueName, exchange, rmqOpts);    
    }

    public void ensure(final String queueName,
                       final String routingQueue,
                       final String exchange) throws Exception {
        ensure(queueName, routingQueue, exchange, rmqOpts());
    }

    public void ensure(final String queueName,
                       final String routingQueue,
                       final String exchange,
                       final Map<String, Object> rmqOpts) throws Exception {
        channel.queueDeclare(queueName, true, false, false, rmqOpts);
        channel.queueBind(queueName, exchange, routingQueue);
        log.info("Created queue: {}", queueName);
    }

    public Map<String, Object> rmqOpts() {
        return ImmutableMap.<String, Object>builder()
                .put("x-ha-policy", "all")
                .put("ha-mode", "all")
                .build();
    }

    public Map<String, Object> rmqOpts(String deadLetterExchange) {
        return ImmutableMap.<String, Object>builder()
                .put("x-ha-policy", "all")
                .put("ha-mode", "all")
                .put("x-dead-letter-exchange", deadLetterExchange)
                .build();
    }

    public HealthCheck healthcheck() {
        return new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                if (connection == null) {
                    log.warn("RMQ Htalthcheck::No RMQ connection available");
                    return Result.unhealthy("No RMQ connection available");
                }
                if (!connection.isOpen()) {
                    log.warn("RMQ Htalthcheck::RMQ connection is not open");
                    return Result.unhealthy("RMQ connection is not open");
                }
                if(null == channel) {
                    log.warn("RMQ Htalthcheck::Producer channel is down");
                    return Result.unhealthy("Producer channel is down");
                }
                if(!channel.isOpen()) {
                    log.warn("RMQ Htalthcheck::Producer channel is closed");
                    return Result.unhealthy("Producer channel is closed");
                }
                return Result.healthy();
            }
        };
    }

    @Override
    public void stop() throws Exception {
        if(null != channel && channel.isOpen()) {
            channel.close();
        }
        if(null != connection && connection.isOpen()) {
            connection.close();
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    public Channel channel() throws IOException {
        return channel;
    }

    public Channel newChannel() throws IOException {
        return connection.createChannel();
    }
    
    private String getSideline(String name){
        return String.format("%s_%s", name, "SIDELINE");
    }
}
