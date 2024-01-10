package io.appform.dropwizard.actors.utils;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class RMQContainerUtils {

    public static final int RABBITMQ_MANAGEMENT_PORT = 15672;
    public static final String RABBITMQ_DOCKER_IMAGE = "rabbitmq:3.8.34-management";
    public static final String RABBITMQ_USERNAME = "guest";
    public static final String RABBITMQ_PASSWORD = "guest";
    public static volatile boolean loaded = false;
    public static RabbitMQContainer rmqContainer;

    public static synchronized RabbitMQContainer startContainer() {
        synchronized (RMQContainerUtils.class) {
            if (!loaded) {
                log.info("Loading RMQ Container");
                RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(
                        DockerImageName.parse(RABBITMQ_DOCKER_IMAGE));
                rabbitMQContainer.start();
//                RabbitMQContainerConfiguration containerConfiguration = new RabbitMQContainerConfiguration();
//                containerConfiguration.setDockerImage(RABBITMQ_DOCKER_IMAGE);
//                containerConfiguration.setWaitTimeoutInSeconds(300L);
//                log.info("Starting rabbitMQ server. Docker image: {}", containerConfiguration.getDockerImage());
//
//                GenericContainer rabbitMQ = new GenericContainer(RABBITMQ_DOCKER_IMAGE).withEnv(
//                                "RABBITMQ_DEFAULT_VHOST", containerConfiguration.getVhost())
//                        .withEnv("RABBITMQ_DEFAULT_USER", RABBITMQ_USERNAME)
//                        .withEnv("RABBITMQ_DEFAULT_PASS", RABBITMQ_PASSWORD)
//                        .withExposedPorts(containerConfiguration.getPort(), RABBITMQ_MANAGEMENT_PORT)
//                        .waitingFor(new RabbitMQStatusCheck(containerConfiguration))
//                        .withStartupTimeout(Duration.ofSeconds(30));
//
//                rabbitMQ = rabbitMQ.withStartupCheckStrategy(new IsRunningStartupCheckStrategyWithDelay());
//                rabbitMQ.start();
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
