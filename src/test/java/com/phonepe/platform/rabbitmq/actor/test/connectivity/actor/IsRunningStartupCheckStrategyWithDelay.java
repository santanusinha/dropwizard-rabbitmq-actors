package com.phonepe.platform.rabbitmq.actor.test.connectivity.actor;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@Slf4j
public class IsRunningStartupCheckStrategyWithDelay extends IsRunningStartupCheckStrategy {

    @Override
    public StartupStatus checkStartupState(DockerClient dockerClient, String containerId) {
        /**
         * See description in {@link com.phonepe.platform.anomalydetection.utils.RabbitMQTestContainerManager#initialize(com.github.qtrouper.core.rabbit.RabbitConfiguration)}
         * for details.
         */
        try {
            await().pollDelay(500, TimeUnit.MILLISECONDS).until(() -> true);
        } catch (Exception e) {
            log.error("Unable to pause thread", e);
        }

        return super.checkStartupState(dockerClient, containerId);
    }
}

