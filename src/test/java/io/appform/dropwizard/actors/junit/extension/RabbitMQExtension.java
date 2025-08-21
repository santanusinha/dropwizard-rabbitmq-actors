package io.appform.dropwizard.actors.junit.extension;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class RabbitMQExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final String RABBITMQ_DOCKER_IMAGE = "rabbitmq:3-management";
    private static final int CONSUMER_TIMEOUT_MS = 60_000;
    private static final String RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS = "RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS";


    @Override
    public void beforeEach(ExtensionContext context) {
        val threadId = Thread.currentThread().getId();
        val store = context.getRoot().getStore(ExtensionContext.Namespace.create(RabbitMQExtension.class, threadId));
        store.getOrComputeIfAbsent(RabbitMQContainer.class, key -> {
            log.info("Loading RMQ Container");
            RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(
                    DockerImageName.parse(RABBITMQ_DOCKER_IMAGE));
            rabbitMQContainer.withEnv(RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS,
                    String.format("-rabbit consumer_timeout %d", CONSUMER_TIMEOUT_MS));
            rabbitMQContainer.start();
            log.info("Started RabbitMQ server on port {}", rabbitMQContainer.getMappedPort(5672));
            return rabbitMQContainer;
        });
    }

    @Override
    public void afterEach(ExtensionContext context) {
        val threadId = Thread.currentThread().getId();
        val store = context.getRoot().getStore(ExtensionContext.Namespace.create(RabbitMQExtension.class, threadId));
        store.get(RabbitMQContainer.class, RabbitMQContainer.class).stop();
        store.remove(RabbitMQContainer.class, RabbitMQContainer.class);
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext,
                                     final ExtensionContext extensionContext) {
        val parameterType = parameterContext.getParameter().getType();
        return parameterType == RabbitMQContainer.class;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext,
                                   final ExtensionContext extensionContext) {
        val threadId = Thread.currentThread().getId();
        val store = extensionContext.getStore(ExtensionContext.Namespace.create(RabbitMQExtension.class, threadId));
        return store.get(parameterContext.getParameter().getType());
    }
}
