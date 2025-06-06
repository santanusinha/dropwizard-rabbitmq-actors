package io.appform.dropwizard.actors.utils;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class RMQContainer {

    public static final int RABBITMQ_MANAGEMENT_PORT = 15672;
    public static final String RABBITMQ_USERNAME = "guest";
    public static final String RABBITMQ_PASSWORD = "guest";
    public static final int CONSUMER_TIMEOUT_MS = 60_000;

}
