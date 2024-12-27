package io.appform.dropwizard.actors.utils;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class RMQContainer {

    public static final int RABBITMQ_MANAGEMENT_PORT = 15672;
    public static final String RABBITMQ_DOCKER_IMAGE = "rabbitmq:3-management";
    public static final String RABBITMQ_USERNAME = "guest";
    public static final String RABBITMQ_PASSWORD = "guest";
    public static final int CONSUMER_TIMEOUT = 60000;
    public static final String RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS = "RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS";
    public static volatile boolean loaded = false;
    private static RabbitMQContainer rmqContainer;

    public static synchronized RabbitMQContainer startContainer() {
        synchronized (RMQContainer.class) {
            if (!loaded) {
                log.info("Loading RMQ Container");
                RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(
                        DockerImageName.parse(RABBITMQ_DOCKER_IMAGE));
                rabbitMQContainer.withEnv(RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS,
                        String.format("-rabbit consumer_timeout %d", CONSUMER_TIMEOUT));
                rabbitMQContainer.start();
                log.info("Started RabbitMQ server");
                loaded = true;
                rmqContainer = rabbitMQContainer;
                return rabbitMQContainer;
            } else {
                log.info("RMQ Container is already started, returning the reference to the previous container");
                return rmqContainer;
            }
        }
    }
}
