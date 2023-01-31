package io.appform.dropwizard.actors.utils;

import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;

import java.util.concurrent.TimeUnit;

@Slf4j
public class IsRunningStartupCheckStrategyWithDelay extends IsRunningStartupCheckStrategy {

    @Override
    public StartupStatus checkStartupState(DockerClient dockerClient, String containerId) {
        /**
         * See description in {@link com.phonepe.platform.anomalydetection.utils.RabbitMQTestContainerManager#initialize(com.github.qtrouper.core.rabbit.RabbitConfiguration)}
         * for details.
         */
        try {
            await().pollDelay(5000, TimeUnit.MILLISECONDS).until(() -> true);
        } catch (Exception e) {
            log.error("Unable to pause thread", e);
        }

        return super.checkStartupState(dockerClient, containerId);
    }
}