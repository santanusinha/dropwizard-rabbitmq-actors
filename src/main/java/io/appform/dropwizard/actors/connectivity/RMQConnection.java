
/*
 * Copyright (c) 2019 Santanu Sinha <santanu.sinha@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.appform.dropwizard.actors.connectivity;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.StandardMetricsCollector;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
public class RMQConnection implements Managed {
    @Getter
    private final RMQConfig config;
    private final String name;
    private Connection connection;
    private Channel channel;
    private final ExecutorService executorService;
    private final Environment environment;

    public RMQConnection(String name,
                         RMQConfig config,
                         ExecutorService executorService,
                         Environment environment) {
        this.name = name;
        this.config = config;
        this.executorService = executorService;
        this.environment = environment;
    }


    @Override
    public void start() throws Exception {
        log.info(String.format("Starting RMQ connection [%s]", name));
        ConnectionFactory factory = new ConnectionFactory();
        factory.setMetricsCollector(new StandardMetricsCollector(environment.metrics(), metricPrefix(name)));
        if (config.isSecure()) {
            factory.setUsername(config.getUserName());
            factory.setPassword(config.getPassword());
            if (Strings.isNullOrEmpty(config.getCertStorePath())) {
                factory.useSslProtocol();
            } else {
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
        } else {
            factory.setUsername(config.getUserName());
            factory.setPassword(config.getPassword());
        }
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(3000);
        factory.setRequestedHeartbeat(60);
        if (!Strings.isNullOrEmpty(config.getVirtualHost())) {
            factory.setVirtualHost(config.getVirtualHost());
        }
        connection = factory.newConnection(executorService,
                config.getBrokers().stream()
                        .map(broker -> new Address(broker.getHost()))
                        .toArray(Address[]::new)
        );
        connection.addBlockedListener(new BlockedListener() {
            @Override
            public void handleBlocked(String reason) throws IOException {
                log.warn(String.format("RMQ Connection [%s] is blocked due to [%s]", name, reason));
            }

            @Override
            public void handleUnblocked() throws IOException {
                log.warn(String.format("RMQ Connection [%s] is unblocked now", name));
            }
        });
        channel = connection.createChannel();
        environment.healthChecks().register(String.format("rmqconnection-%s", connection), healthcheck());
        log.info(String.format("Started RMQ connection [%s] ", name));
    }

    private String metricPrefix(String name) {
        return String.format("rmqconnection.%s", NamingUtils.sanitizeMetricName(name));
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
        log.info("Created queue: {} bound to {}", queueName, exchange);
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

    public Map<String, Object> rmqOpts(boolean enableQueueTTL, Duration ttl) {
        final Map<String, Object> ttlOpts = getTTLOpts(enableQueueTTL, ttl);
        return ImmutableMap.<String, Object>builder()
                .putAll(ttlOpts)
                .put("x-ha-policy", "all")
                .put("ha-mode", "all")
                .build();
    }

    public Map<String, Object> rmqOpts(String deadLetterExchange, boolean enableQueueTTL, Duration ttl) {
        final Map<String, Object> ttlOpts = getTTLOpts(enableQueueTTL, ttl);
        return ImmutableMap.<String, Object>builder()
                .putAll(ttlOpts)
                .put("x-ha-policy", "all")
                .put("ha-mode", "all")
                .put("x-dead-letter-exchange", deadLetterExchange)
                .build();
    }

    private Map<String, Object> getTTLOpts(boolean enableQueueTTL, Duration ttl) {
        final Map<String, Object> ttlOpts = new HashMap<>();
        if (enableQueueTTL) {
            ttlOpts.put("x-expires", ttl.getSeconds()*1000);
        }

        return ttlOpts;
    }
    public HealthCheck healthcheck() {
        return new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                if (connection == null) {
                    log.warn("RMQ Healthcheck::No RMQ connection available");
                    return Result.unhealthy("No RMQ connection available");
                }
                if (!connection.isOpen()) {
                    log.warn("RMQ Healthcheck::RMQ connection is not open");
                    return Result.unhealthy("RMQ connection is not open");
                }
                if (null == channel) {
                    log.warn("RMQ Healthcheck::Producer channel is down");
                    return Result.unhealthy("Producer channel is down");
                }
                if (!channel.isOpen()) {
                    log.warn("RMQ Healthcheck::Producer channel is closed");
                    return Result.unhealthy("Producer channel is closed");
                }
                return Result.healthy();
            }
        };
    }

    @Override
    public void stop() throws Exception {
        if (null != channel && channel.isOpen()) {
            channel.close();
        }
        if (null != connection && connection.isOpen()) {
            connection.close();
        }
    }

    public Channel channel() throws IOException {
        return channel;
    }

    public Channel newChannel() throws IOException {
        return connection.createChannel();
    }

    private String getSideline(String name) {
        return String.format("%s_%s", name, "SIDELINE");
    }
}
