package io.appform.dropwizard.actors.utils;

import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.testcontainers.rabbitmq.RabbitMQStatusCheck;
import io.appform.testcontainers.rabbitmq.config.RabbitMQContainerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;

import java.util.Collections;

import static io.appform.testcontainers.commons.ContainerUtils.containerLogsConsumer;

@Slf4j
public class RabbitMQTestContainerManager {
    private static GenericContainer rabbitMQ;

    @SuppressWarnings("unchecked")
    public static RMQConfig initialize() {
        RabbitMQContainerConfiguration containerConfiguration = rmqContainerConfiguration();
        log.info("Starting rabbitMQ server. Docker image: {}", containerConfiguration.getDockerImage());

        containerConfiguration.setWaitTimeoutInSeconds(300L);
        rabbitMQ = new GenericContainer(containerConfiguration.getDockerImage())
                .withEnv("RABBITMQ_DEFAULT_VHOST", containerConfiguration.getVhost())
                .withEnv("RABBITMQ_DEFAULT_USER", containerConfiguration.getUser())
                .withEnv("RABBITMQ_DEFAULT_PASS", containerConfiguration.getPassword())
                .withLogConsumer(containerLogsConsumer(log))
                .withExposedPorts(containerConfiguration.getPort())
                .waitingFor(rabbitMQStartupCheckStrategy(containerConfiguration))
                .withStartupTimeout(containerConfiguration.getTimeoutDuration());

        rabbitMQ = rabbitMQ.withStartupCheckStrategy(new IsRunningStartupCheckStrategyWithDelay());
        rabbitMQ.start();

        return finalRMQConfig(rabbitMQ, containerConfiguration);
    }

    public static void cleanUp() {
        if (rabbitMQ != null) {
            rabbitMQ.stop();
            rabbitMQ = null;
        }
    }

    private static RabbitMQStatusCheck rabbitMQStartupCheckStrategy(RabbitMQContainerConfiguration containerConfiguration) {
        return new RabbitMQStatusCheck(containerConfiguration);
    }

    private static RabbitMQContainerConfiguration rmqContainerConfiguration() {
        RabbitMQContainerConfiguration rabbitMQContainerConfiguration = new RabbitMQContainerConfiguration();
        rabbitMQContainerConfiguration.setDockerImage("rabbitmq:3.8-alpine");
        return rabbitMQContainerConfiguration;

    }

    private static RMQConfig finalRMQConfig(GenericContainer rabbitmqContainer,
                                            RabbitMQContainerConfiguration rmqContainerConfiguration) {
        RMQConfig rmqConfig = new RMQConfig();
        Integer mappedPort = rabbitmqContainer.getMappedPort(rmqContainerConfiguration.getPort());
        String host = rabbitmqContainer.getContainerIpAddress();
        rmqConfig.setBrokers(Collections.singletonList(Broker.builder()
                .host(host)
                .port(mappedPort)
                .build()));
        rmqConfig.setUserName(rmqContainerConfiguration.getUser());
        rmqConfig.setPassword(rmqContainerConfiguration.getPassword());
        rmqConfig.setVirtualHost(rmqContainerConfiguration.getVhost());
        rmqConfig.setThreadPoolSize(1);
        log.info("Started RabbitMQ server. Connection details: {}", rmqConfig);
        return rmqConfig;

    }
}