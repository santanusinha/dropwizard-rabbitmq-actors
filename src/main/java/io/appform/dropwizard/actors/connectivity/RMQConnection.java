
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.StandardMetricsCollector;
import io.appform.dropwizard.actors.config.RMQConfig;
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

@Slf4j
public class RMQConnection implements Managed {
    @Getter
    private final RMQConfig config;
    @VisibleForTesting
    @Getter
    private Connection connection;
    private Channel channel;
    private final MetricRegistry metricRegistry;
    private final ExecutorService executorService;

    public RMQConnection(RMQConfig config, MetricRegistry metricRegistry, ExecutorService executorService) {
        this.config = config;
        this.metricRegistry = metricRegistry;
        this.executorService = executorService;
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
        connection = factory.newConnection(executorService,
                config.getBrokers().stream()
                        .map(broker -> new Address(broker.getHost()))
                        .toArray(Address[]::new)
        );
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
