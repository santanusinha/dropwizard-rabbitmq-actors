
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

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.StandardMetricsCollector;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.QueueTypeVisitorImpl;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.observers.RMQObserver;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import javax.net.ssl.SSLContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;

@Slf4j
public class RMQConnection implements Managed {
    @Getter
    private final RMQConfig config;
    private final String name;
    private final ExecutorService executorService;
    private final Environment environment;
    private final TtlConfig ttlConfig;
    private Connection connection;
    private Channel channel;

    @Getter
    private final RMQObserver rootObserver;


    public RMQConnection(final String name,
                         final RMQConfig config,
                         final ExecutorService executorService,
                         final Environment environment,
                         final TtlConfig ttlConfig,
                         final RMQObserver rootObserver) {
        this.name = name;
        this.config = config;
        this.executorService = executorService;
        this.environment = environment;
        this.ttlConfig = ttlConfig;
        this.rootObserver = rootObserver;
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
                Preconditions.checkNotNull(config.getCertPassword(),
                        "Cert password is required if cert file path has been provided");
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(new FileInputStream(config.getCertStorePath()), config.getCertPassword().toCharArray());

                KeyStore tks = KeyStore.getInstance("JKS");
                tks.load(new FileInputStream(config.getServerCertStorePath()),
                        config.getServerCertPassword().toCharArray());
                SSLContext c = SSLContexts.custom()
                        .setProtocol("TLSv1.2")
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
                        .map(broker -> new Address(broker.getHost(), broker.getPort()))
                        .toArray(Address[]::new),
                name
        );
        connection.addBlockedListener(new BlockedListener() {
            @Override
            public void handleBlocked(String reason) {
                log.warn(String.format("RMQ Connection [%s] is blocked due to [%s]", name, reason));
            }

            @Override
            public void handleUnblocked() {
                log.warn(String.format("RMQ Connection [%s] is unblocked now", name));
            }
        });
        channel = connection.createChannel();
        environment.healthChecks().register(String.format("rmqconnection-%s-%s", connection, UUID.randomUUID()), healthcheck());
        log.info(String.format("Started RMQ connection [%s] ", name));
    }

    private String metricPrefix(String name) {
        return String.format("rmqconnection.%s", NamingUtils.sanitizeMetricName(name));
    }

    public void ensure(final String queueName,
                       final String exchange,
                       final Map<String, Object> rmqOpts) throws Exception {
        ensure(queueName, queueName, exchange, rmqOpts);
    }

    public void ensure(final String queueName,
                       final String routingQueue,
                       final String exchange,
                       final Map<String, Object> rmqOpts) throws Exception {
        channel.queueDeclare(queueName, true, false, false, rmqOpts);
        channel.queueBind(queueName, exchange, routingQueue);
        log.info("Created queue: {} bound to {}", queueName, exchange);
    }

    public void addBinding(String queueName, String exchange, String routingKey) throws Exception {
        channel.queueBind(queueName, exchange, routingKey);
        log.info("Created binding for queue : {} bound to {} routing Key {}", queueName, exchange, routingKey);
    }

    public Map<String, Object> rmqOpts(final ActorConfig actorConfig) {
        final Map<String, Object> ttlOpts = getActorTTLOpts(actorConfig.getTtlConfig());
        final Map<String, Object> priorityOpts = getPriorityOpts(actorConfig);
        Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                .putAll(ttlOpts)
                .putAll(priorityOpts);
        builder.putAll(actorConfig.getQueueType()
                .handleConfig(new QueueTypeVisitorImpl(actorConfig)));
        return builder.build();
    }

    public Map<String, Object> rmqOpts(final String deadLetterExchange,
                                       final ActorConfig actorConfig) {
        final Map<String, Object> ttlOpts = getActorTTLOpts(actorConfig.getTtlConfig());
        final Map<String, Object> priorityOpts = getPriorityOpts(actorConfig);
        Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                .putAll(ttlOpts)
                .putAll(priorityOpts)
                .put("x-dead-letter-exchange", deadLetterExchange);
        builder.putAll(actorConfig.getQueueType()
                .handleConfig(new QueueTypeVisitorImpl(actorConfig)));
        return builder.build();
    }

    public HealthCheck healthcheck() {
        return new HealthCheck() {
            @Override
            protected Result check() {
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

    public Channel channel() {
        return channel;
    }

    public Channel newChannel() throws IOException {
        return connection.createChannel();
    }

    private Map<String, Object> getActorTTLOpts(final TtlConfig ttlConfig) {
        if (ttlConfig != null) {
            return getTTLOpts(ttlConfig);
        }
        return getTTLOpts(this.ttlConfig);
    }

    private Map<String, Object> getTTLOpts(final TtlConfig ttlConfig) {
        final Map<String, Object> ttlOpts = new HashMap<>();
        if (ttlConfig != null && ttlConfig.isTtlEnabled()) {
            ttlOpts.put("x-expires", ttlConfig.getTtl().getSeconds() * 1000);
        }
        return ttlOpts;
    }

    private Map<String, Object> getPriorityOpts(final ActorConfig actorConfig) {
        if(actorConfig.isPriorityQueue()) {
            return ImmutableMap.<String, Object>builder()
                    .put("x-max-priority", actorConfig.getMaxPriority())
                    .build();
        } else {
            return Collections.emptyMap();
        }
    }

}
