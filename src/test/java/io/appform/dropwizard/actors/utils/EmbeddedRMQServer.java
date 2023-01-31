package io.appform.dropwizard.actors.utils;

import io.appform.dropwizard.actors.config.RMQConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmbeddedRMQServer {
    @Getter
    private static RMQConfig config;

    public static void start() {
        try {
            RMQConfig config = RabbitMQTestContainerManager.initialize();
            EmbeddedRMQServer.config = config;
            log.error("config {}", config);
        } catch (Exception e) {
            log.error("Failed to start RMQ");
        }
    }

    public static void stop() {
        RabbitMQTestContainerManager.cleanUp();
    }
}
