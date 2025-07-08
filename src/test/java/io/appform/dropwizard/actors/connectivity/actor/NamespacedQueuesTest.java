package io.appform.dropwizard.actors.connectivity.actor;


import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.junit.extension.RabbitMQExtension;
import io.appform.dropwizard.actors.metrics.RMQMetricObserver;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.retry.config.CountLimitedFixedWaitRetryConfig;
import io.appform.dropwizard.actors.utils.*;
import io.dropwizard.Configuration;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardAppExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.validation.Validation;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;


@Slf4j
@ExtendWith(RabbitMQExtension.class)
public class NamespacedQueuesTest {

    private static final Retryer<Void> ASSERTION_RETRYER = RetryerBuilder.<Void>newBuilder()
            .retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(10, TimeUnit.MILLISECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(10))
            .build();

    private static final String NAMESPACE_VALUE = "namespace1";

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleTestApplication.class);
    private final MetricRegistry metricRegistry = new MetricRegistry();

    @BeforeAll
    @SneakyThrows
    public static void setupClass() {
        app.before();
    }

    @AfterAll
    @SneakyThrows
    public static void cleanupClass() {
        app.after();
    }

    @BeforeEach
    public void setup() {
        System.setProperty(NamingUtils.NAMESPACE_PROPERTY_NAME, NAMESPACE_VALUE);
    }

    private RMQConfig config;

    /**
     * This test does the following:
     * - Sets the FEATURE_ENV_NAME system property
     * - Launches a RabbitMQ instance in a Docker container
     * - Launches a dummy Dropwizard app for fetching its Environment
     * - Calls /api/queues on the RabbitMQ instance and verifies the names
     */
    @Test
    public void testQueuesAreNamespacedWhenFeatureEnvIsSet(final RabbitMQContainer rabbitMQContainer) throws Exception {
        String queueName = "publisher-0";
        val mappedManagementPort = rabbitMQContainer.getMappedPort(RMQTestUtils.RABBITMQ_MANAGEMENT_PORT);
        config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        RMQConnection connection = new RMQConnection("test-conn-0", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));
        connection.start();

        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-0");
        UnmanagedPublisher publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, null);
        publisher.start();

        ObjectMapper objectMapper = new ObjectMapper();
        Response response = sendRequest("/api/queues", mappedManagementPort);
        String queueNameWithNamespace = String.format("%s.%s.%s", NAMESPACE_VALUE, actorConfig.getPrefix(), queueName);
        if (response != null) {
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.isArray()) {
                for (JsonNode json : jsonNode) {
                    String actualQueueName = json.get("name").asText();
                    if (actualQueueName.endsWith(queueName)) {
                        Assertions.assertEquals(queueNameWithNamespace, actualQueueName);
                    }
                }
            }
            response.close();
        }
    }

    @AfterEach
    public void cleanup() {
        System.clearProperty(NamingUtils.NAMESPACE_PROPERTY_NAME);
    }

    @Test
    public void testQueuesAreNotNamespacedWhenFeatureEnvNotSet(final RabbitMQContainer rabbitMQContainer) throws Exception {
        String queueName = "publisher-1";
        System.clearProperty(NamingUtils.NAMESPACE_PROPERTY_NAME);
        val mappedManagementPort = rabbitMQContainer.getMappedPort(RMQTestUtils.RABBITMQ_MANAGEMENT_PORT);
        config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        RMQConnection connection = new RMQConnection("test-conn-1", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));
        connection.start();

        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        UnmanagedPublisher publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, null);
        publisher.start();

        ObjectMapper objectMapper = new ObjectMapper();
        Response response = sendRequest("/api/queues", mappedManagementPort);
        String queueNameWithNamespace = String.format("%s.%s.%s", NAMESPACE_VALUE, actorConfig.getPrefix(), queueName);
        if (response != null) {
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.isArray()) {
                for (JsonNode json : jsonNode) {
                    String actualQueueName = json.get("name")
                            .asText();
                    Assertions.assertNotEquals(actualQueueName, queueNameWithNamespace);
                }
            }
            response.close();
        }
    }

    @Test
    public void testQueuesAreRemovedAfterTtl(final RabbitMQContainer rabbitMQContainer) throws Exception {
        String queueName = "publisher-2";
        val mappedManagementPort = rabbitMQContainer.getMappedPort(RMQTestUtils.RABBITMQ_MANAGEMENT_PORT);
        config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        TtlConfig ttlConfig = TtlConfig.builder()
                .ttlEnabled(true)
                .ttl(Duration.ofSeconds(2))
                .build();
        RMQConnection connection = new RMQConnection("test-conn-2", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), ttlConfig, new RMQMetricObserver(config, metricRegistry));
        connection.start();

        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-2");
        UnmanagedPublisher publisher = new UnmanagedPublisher<>(NamingUtils.queueName(actorConfig.getPrefix(), queueName), actorConfig, connection, null);
        publisher.start();

        Thread.sleep(4_000);
        Response response = sendRequest("/api/queues", mappedManagementPort);
        if (response != null) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.isArray()) {
                for (JsonNode json : jsonNode) {
                    String actualQueueName = json.get("name")
                            .asText();
                    Assertions.assertFalse(actualQueueName.contains(queueName));
                }
            }
            response.close();
        }
    }

    private RMQFetchMessages getBody() {
        return new RMQFetchMessages();
    }

    private Response sendRequest(String endpoint,
                                 int mappedManagementPort) {
        OkHttpClient client = new OkHttpClient();
        String credential = Credentials.basic(config.getUserName(), config.getPassword());
        Request request = new Request.Builder().url("http://" + config.getBrokers()
                        .get(0)
                        .getHost() + ":" + mappedManagementPort + endpoint)
                .header("Accept", "application/json")
                .header("Authorization", credential)
                .build();

        try {
            return client.newCall(request)
                    .execute();
        } catch (IOException e) {
            log.error("Error while making API call to RabbitMQ", e);
        }
        return null;
    }

    private Response sendPostRequest(String endpoint,
                                     String body,
                                     int mappedManagementPort) {
        OkHttpClient client = new OkHttpClient();
        String credential = Credentials.basic(config.getUserName(), config.getPassword());
        Request request = new Request.Builder().url("http://" + config.getBrokers()
                        .get(0)
                        .getHost() + ":" + mappedManagementPort + endpoint)
                .header("Accept", "application/json")
                .header("Authorization", credential)
                .post(RequestBody.create(body.getBytes(StandardCharsets.UTF_8)))
                .build();

        try {
            return client.newCall(request)
                    .execute();
        } catch (IOException e) {
            log.error("Error while making API call to RabbitMQ", e);
        }
        return null;
    }

    private Response sendPutRequest(String endpoint,
                                     String body,
                                     int mappedManagementPort) {
        final var client = new OkHttpClient();
        final var credential = Credentials.basic(config.getUserName(), config.getPassword());
        final var request = new Request.Builder().url("http://" + config.getBrokers()
                        .get(0)
                        .getHost() + ":" + mappedManagementPort + endpoint)
                .header("Accept", "application/json")
                .header("Authorization", credential)
                .put(RequestBody.create(body.getBytes(StandardCharsets.UTF_8)))
                .build();

        try {
            return client.newCall(request)
                    .execute();
        } catch (IOException e) {
            log.error("Error while making API call to RabbitMQ", e);
        }
        return null;
    }

    @Test
    public void testQueuesSidelineForFailedMessages(final RabbitMQContainer rabbitMQContainer) throws Exception {
        val mappedManagementPort = rabbitMQContainer.getMappedPort(RMQTestUtils.RABBITMQ_MANAGEMENT_PORT);
        config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        RMQConnection connection = new RMQConnection("test-conn-3", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), null, new RMQMetricObserver(config, metricRegistry));
        connection.start();

        ActorConfig actorConfig = AsyncOperationHelper.buildActorConfig();
        val objectMapper = Jackson.newObjectMapper();
        Environment environment = new Environment("testing",
                objectMapper,
                Validation.buildDefaultValidatorFactory(),
                new MetricRegistry(),
                Thread.currentThread().getContextClassLoader(),
                new HealthCheckRegistry(),
                new Configuration());
        ConnectionRegistry registry = new ConnectionRegistry(environment,
                (name, coreSize) -> Executors.newFixedThreadPool(1),
                config, TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));
        SidelineTestActor actor = new SidelineTestActor(actorConfig, registry, objectMapper,
                new RetryStrategyFactory(), new ExceptionHandlingFactory());
        actor.start();
        TestMessage message = TestMessage.builder()
                .actorType(ActorType.ALWAYS_FAIL_ACTOR)
                .name("test_message")
                .build();
        actor.publish(message);

        Thread.sleep(10000);
        String sidelineQueue = NAMESPACE_VALUE + ".test.ALWAYS_FAIL_ACTOR_SIDELINE";
        String endpoint = "/api/queues/%2F/" + sidelineQueue + "/get";

        Response response = sendPostRequest(endpoint, objectMapper.writeValueAsString(getBody()), mappedManagementPort);
        Assertions.assertNotNull(response);

        JsonNode jsonNode = objectMapper.readTree(response.body().string());
        Assertions.assertEquals(1, jsonNode.size());
        JsonNode messageResponse = jsonNode.get(0);
        Assertions.assertEquals("test.exchange_SIDELINE", messageResponse.get("exchange")
                .asText());
        TestMessage actualMessage = objectMapper.readValue(messageResponse.get("payload").asText(), TestMessage.class);
        Assertions.assertEquals(ActorType.ALWAYS_FAIL_ACTOR, actualMessage.getActorType());
        Assertions.assertEquals("test_message", actualMessage.getName());
        response.close();
    }


    /**
     * This test does the following:
     * - Launches a RabbitMQ instance in a Docker container added bash command to start rmq server with shovel component
     * - Launches a dummy Dropwizard app for fetching its Environment
     * - Publishes Message on SidelineProcessorTestActor and fails and moves to sideline which has SidelineProcessorRetryConfig for 3 attempts
     * - Calls Shovel API to move the messages from sideline to Sideline processor queue
     * - Sideline Processor queue is configured to handle the messages successfully for 2nd attempt
     * - Validates the number of messages in the sideline queue and sideline processing queue to be 0
     */
    @Test
    public void testQueueSidelineProcessorWithRetryerForFailedMessages(final RabbitMQContainer rabbitMQContainer) throws Exception {
        final var  mappedManagementPort = rabbitMQContainer.getMappedPort(RMQTestUtils.RABBITMQ_MANAGEMENT_PORT);
        config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        final var  connection = new RMQConnection("test-conn-4", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), null, new RMQMetricObserver(config, metricRegistry));
        connection.start();

        final var  actorConfig = AsyncOperationHelper.buildActorConfigWithSidelineProcessorEnabled();
        actorConfig.setSidelineProcessorRetryConfig(CountLimitedFixedWaitRetryConfig.builder()
                .maxAttempts(3)
                .waitTime(io.dropwizard.util.Duration.milliseconds(1))
                .build());
        final var  objectMapper = Jackson.newObjectMapper();
        final var  environment = new Environment("testing",
                objectMapper,
                Validation.buildDefaultValidatorFactory(),
                new MetricRegistry(),
                Thread.currentThread().getContextClassLoader(),
                new HealthCheckRegistry(),
                new Configuration());
        final var  registry = new ConnectionRegistry(environment,
                (name, coreSize) -> Executors.newFixedThreadPool(1),
                config, TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));
        final var  actor = new SidelineProcessorTestActor(actorConfig, registry, objectMapper,
                new RetryStrategyFactory(), new ExceptionHandlingFactory());
        actor.start();
        final var  message = TestMessage.builder()
                .actorType(ActorType.ALWAYS_FAIL_ACTOR)
                .name("test_message")
                .sidelineProcessorQueueHandleSuccessCount(2)
                .build();
        actor.publish(message);

        // Ensure the original handle is called once
        ASSERTION_RETRYER.call(() -> {
            if (actor.getHandleCalledCount() != 1) {
                throw new IllegalStateException("Expected 1 handle call, got " + actor.getHandleCalledCount());
            }
            return null;
        });

        Assertions.assertEquals(1,actor.getHandleCalledCount());
        final var  sidelineQueue = NAMESPACE_VALUE + ".test.ALWAYS_FAIL_ACTOR_SIDELINE";

        assertCountOfMessagesInQueue(sidelineQueue, 1, mappedManagementPort, objectMapper);


        //Move Message to sideline processing queue
        final var  sidelineProcessingQueue = NAMESPACE_VALUE + ".test.ALWAYS_FAIL_ACTOR_SIDELINE_PROCESSOR";
        final var  shovelName  = "MoveToSidelineProcessing";
        final var  shovelEndpoint = "/api/parameters/shovel/%2F/" + shovelName;

        final var shovelBody =  RMQShovelMessages.builder()
                .shovelRequestName(shovelName)
                .value(RmqShovelRequestValue.builder()
                        .sourceQueue(sidelineQueue)
                        .destinationQueue(sidelineProcessingQueue)
                        .user(config.getUserName())
                        .virtualHost(config.getVirtualHost())
                        .build())
                .build();

        final var shovelBodyString = objectMapper.writeValueAsString(shovelBody);

        final var  shovelMessageResponse = sendPutRequest(shovelEndpoint, shovelBodyString, mappedManagementPort);
        Assertions.assertNotNull(shovelMessageResponse);

        // Retry until exactly 2 sideline processor attempts have occurred
        ASSERTION_RETRYER.call(() -> {
            if (actor.getHandleSidelineProcessorCalledCount() != 2) {
                throw new IllegalStateException("Waiting for 3 sideline processor calls");
            }
            return null;
        });

        Assertions.assertEquals(2,actor.getHandleSidelineProcessorCalledCount());

        // Check the number of messages in sideline queue
        assertCountOfMessagesInQueue(sidelineQueue, 0, mappedManagementPort, objectMapper);


        // Check the number of messages in sideline processing queue
        assertCountOfMessagesInQueue(sidelineProcessingQueue, 0, mappedManagementPort, objectMapper);
    }


    /**
     * This test does the following:
     * - Launches a RabbitMQ instance in a Docker container added bash command to start rmq server with shovel component
     * - Launches a dummy Dropwizard app for fetching its Environment
     * - Publishes Message on SidelineProcessorTestActor and fails and moves to sideline which has SidelineProcessorRetryConfig for 3 attempts
     * - Calls Shovel API to move the messages from sideline to Sideline processor queue
     * - Sideline Processor queue is configured to fail for all the retry attempts
     * - Validates the number of messages in the sideline queue to be 1 and sideline processing queue to be 0
     */
    @Test
    public void testQueueSidelineProcessorWithRetryerFailureForFailedMessages(final RabbitMQContainer rabbitMQContainer) throws Exception {
        final var mappedManagementPort = rabbitMQContainer.getMappedPort(RMQTestUtils.RABBITMQ_MANAGEMENT_PORT);
        config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        final var connection = new RMQConnection("test-conn-5", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), null, new RMQMetricObserver(config, metricRegistry));
        connection.start();

        final var actorConfig = AsyncOperationHelper.buildActorConfigWithSidelineProcessorEnabled();
        actorConfig.setSidelineProcessorRetryConfig(CountLimitedFixedWaitRetryConfig.builder()
                .maxAttempts(3)
                .waitTime(io.dropwizard.util.Duration.milliseconds(1))
                .build());

        final var objectMapper = Jackson.newObjectMapper();
        final var environment = new Environment("testing",
                objectMapper,
                Validation.buildDefaultValidatorFactory(),
                new MetricRegistry(),
                Thread.currentThread().getContextClassLoader(),
                new HealthCheckRegistry(),
                new Configuration());

        final var registry = new ConnectionRegistry(environment,
                (name, coreSize) -> Executors.newFixedThreadPool(1),
                config, TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));

        final var actor = new SidelineProcessorTestActor(actorConfig, registry, objectMapper,
                new RetryStrategyFactory(), new ExceptionHandlingFactory());
        actor.start();

        final var message = TestMessage.builder()
                .actorType(ActorType.ALWAYS_FAIL_ACTOR)
                .name("test_message")
                .sidelineProcessorQueueHandleSuccessCount(-1)
                .build();

        actor.publish(message);

        // Ensure the original handle is called once
        ASSERTION_RETRYER.call(() -> {
            if (actor.getHandleCalledCount() != 1) {
                throw new IllegalStateException("Expected 1 handle call, got " + actor.getHandleCalledCount());
            }
            return null;
        });

        final var sidelineQueue = NAMESPACE_VALUE + ".test.ALWAYS_FAIL_ACTOR_SIDELINE";
        assertCountOfMessagesInQueue(sidelineQueue, 1, mappedManagementPort, objectMapper);

        // Move message from sideline to sideline processing queue
        final var sidelineProcessingQueue = NAMESPACE_VALUE + ".test.ALWAYS_FAIL_ACTOR_SIDELINE_PROCESSOR";
        final var shovelName = "MoveToSidelineProcessing";
        final var shovelEndpoint = "/api/parameters/shovel/%2F/" + shovelName;

        final var shovelBody = RMQShovelMessages.builder()
                .shovelRequestName(shovelName)
                .value(RmqShovelRequestValue.builder()
                        .sourceQueue(sidelineQueue)
                        .destinationQueue(sidelineProcessingQueue)
                        .user(config.getUserName())
                        .virtualHost(config.getVirtualHost())
                        .build())
                .build();

        final var shovelBodyString = objectMapper.writeValueAsString(shovelBody);
        final var shovelMessageResponse = sendPutRequest(shovelEndpoint, shovelBodyString, mappedManagementPort);
        Assertions.assertNotNull(shovelMessageResponse);

        // Retry until exactly 3 sideline processor attempts have occurred
        ASSERTION_RETRYER.call(() -> {
            if (actor.getHandleSidelineProcessorCalledCount() != 3) {
                throw new IllegalStateException("Waiting for 3 sideline processor calls");
            }
            return null;
        });

        // Assert that sideline queue is now empty
        assertCountOfMessagesInQueue(sidelineQueue, 1, mappedManagementPort, objectMapper);

        // Final validation that 3rd retry hasn't happened
        Assertions.assertEquals(3, actor.getHandleSidelineProcessorCalledCount());

        // Also validate sideline processing queue is empty
        assertCountOfMessagesInQueue(sidelineProcessingQueue, 0, mappedManagementPort, objectMapper);
    }
    /**
     * This test does the following:
     * - Launches a RabbitMQ instance in a Docker container added bash command to start rmq server with shovel component
     * - Launches a dummy Dropwizard app for fetching its Environment
     * - Publishes Message on SidelineProcessorTestActor and fails and moves to sideline
     * - Calls Shovel API to move the messages from sideline to Sideline processor queue
     * - Sideline Processor queue is configured to handle the messages successfully
     * - Validates the number of messages in the sideline queue and sideline processing queue to be 0
     */
    @Test
    public void testQueueSidelineProcessorWithoutRetryerForFailedMessages(final RabbitMQContainer rabbitMQContainer) throws Exception {
        final var mappedManagementPort = rabbitMQContainer.getMappedPort(RMQTestUtils.RABBITMQ_MANAGEMENT_PORT);
        config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        final var  connection = new RMQConnection("test-conn-6", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), null, new RMQMetricObserver(config, metricRegistry));
        connection.start();

        final var  actorConfig = AsyncOperationHelper.buildActorConfigWithSidelineProcessorEnabled();
        final var  objectMapper = Jackson.newObjectMapper();
        final var  environment = new Environment("testing",
                objectMapper,
                Validation.buildDefaultValidatorFactory(),
                new MetricRegistry(),
                Thread.currentThread().getContextClassLoader(),
                new HealthCheckRegistry(),
                new Configuration());
        final var  registry = new ConnectionRegistry(environment,
                (name, coreSize) -> Executors.newFixedThreadPool(1),
                config, TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));
        final var  actor = new SidelineProcessorTestActor(actorConfig, registry, objectMapper,
                new RetryStrategyFactory(), new ExceptionHandlingFactory());
        actor.start();
        final var  message = TestMessage.builder()
                .actorType(ActorType.ALWAYS_FAIL_ACTOR)
                .name("test_message")
                .sidelineProcessorQueueHandleSuccessCount(1)
                .build();
        actor.publish(message);

        // Ensure the original handle is called once
        ASSERTION_RETRYER.call(() -> {
            if (actor.getHandleCalledCount() != 1) {
                throw new IllegalStateException("Expected 1 handle call, got " + actor.getHandleCalledCount());
            }
            return null;
        });

        Assertions.assertEquals(1,actor.getHandleCalledCount());
        final var  sidelineQueue = NAMESPACE_VALUE + ".test.ALWAYS_FAIL_ACTOR_SIDELINE";

        assertCountOfMessagesInQueue(sidelineQueue, 1, mappedManagementPort, objectMapper);


        //Move Message to sideline processing queue
        final var  sidelineProcessingQueue = NAMESPACE_VALUE + ".test.ALWAYS_FAIL_ACTOR_SIDELINE_PROCESSOR";
        final var  shovelName  = "MoveToSidelineProcessing";
        final var  shovelEndpoint = "/api/parameters/shovel/%2F/" + shovelName;

        final var shovelBody =  RMQShovelMessages.builder()
                .shovelRequestName(shovelName)
                .value(RmqShovelRequestValue.builder()
                        .sourceQueue(sidelineQueue)
                        .destinationQueue(sidelineProcessingQueue)
                        .user(config.getUserName())
                        .virtualHost(config.getVirtualHost())
                        .build())
                .build();

        final var shovelBodyString = objectMapper.writeValueAsString(shovelBody);

        final var  shovelMessageResponse = sendPutRequest(shovelEndpoint, shovelBodyString, mappedManagementPort);
        Assertions.assertNotNull(shovelMessageResponse);


        // Retry until exactly 3 sideline processor attempts have occurred
        ASSERTION_RETRYER.call(() -> {
            if (actor.getHandleSidelineProcessorCalledCount() != 1) {
                throw new IllegalStateException("Waiting for 1 sideline processor calls");
            }
            return null;
        });
        Assertions.assertEquals(1,actor.getHandleSidelineProcessorCalledCount());

        // Check the number of messages in sideline queue
        assertCountOfMessagesInQueue(sidelineQueue, 0, mappedManagementPort, objectMapper);


        // Check the number of messages in sideline processing queue
        assertCountOfMessagesInQueue(sidelineProcessingQueue, 0, mappedManagementPort, objectMapper);
    }


    /**
     * This test does the following:
     * - Launches a RabbitMQ instance in a Docker container added bash command to start rmq server with shovel component
     * - Launches a dummy Dropwizard app for fetching its Environment
     * - Publishes Message on SidelineProcessorTestActor and fails and moves to sideline
     * - Calls Shovel API to move the messages from sideline to Sideline processor queue
     * - Validates the number of messages in the sideline queue to be 1 and sideline processing queue to be 0
     */
    @Test
    public void testQueueSidelineProcessorWithoutRetryerFailureForFailedMessages(final RabbitMQContainer rabbitMQContainer) throws Exception {
        final var  mappedManagementPort = rabbitMQContainer.getMappedPort(RMQTestUtils.RABBITMQ_MANAGEMENT_PORT);
        config = RMQTestUtils.getRMQConfig(rabbitMQContainer);

        final var  connection = new RMQConnection("test-conn-7", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), null, new RMQMetricObserver(config, metricRegistry));
        connection.start();

        final var  actorConfig = AsyncOperationHelper.buildActorConfigWithSidelineProcessorEnabled();

        final var  objectMapper = Jackson.newObjectMapper();
        final var  environment = new Environment("testing",
                objectMapper,
                Validation.buildDefaultValidatorFactory(),
                new MetricRegistry(),
                Thread.currentThread().getContextClassLoader(),
                new HealthCheckRegistry(),
                new Configuration());
        final var  registry = new ConnectionRegistry(environment,
                (name, coreSize) -> Executors.newFixedThreadPool(1),
                config, TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));
        final var  actor = new SidelineProcessorTestActor(actorConfig, registry, objectMapper,
                new RetryStrategyFactory(), new ExceptionHandlingFactory());
        actor.start();
        final var  message = TestMessage.builder()
                .actorType(ActorType.ALWAYS_FAIL_ACTOR)
                .name("test_message")
                .sidelineProcessorQueueHandleSuccessCount(-1)
                .build();
        actor.publish(message);

        // Ensure the original handle is called once
        ASSERTION_RETRYER.call(() -> {
            if (actor.getHandleCalledCount() != 1) {
                throw new IllegalStateException("Expected 1 handle call, got " + actor.getHandleCalledCount());
            }
            return null;
        });

        Assertions.assertEquals(1, actor.getHandleCalledCount());
        final var  sidelineQueue = NAMESPACE_VALUE + ".test.ALWAYS_FAIL_ACTOR_SIDELINE";

        assertCountOfMessagesInQueue(sidelineQueue, 1, mappedManagementPort, objectMapper);

        //Move Message to sideline processing queue
        final var  sidelineProcessingQueue = NAMESPACE_VALUE + ".test.ALWAYS_FAIL_ACTOR_SIDELINE_PROCESSOR";
        final var  shovelName  = "MoveToSidelineProcessing";
        final var  shovelEndpoint = "/api/parameters/shovel/%2F/" + shovelName;

        final var shovelBody =  RMQShovelMessages.builder()
                .shovelRequestName(shovelName)
                .value(RmqShovelRequestValue.builder()
                        .sourceQueue(sidelineQueue)
                        .destinationQueue(sidelineProcessingQueue)
                        .user(config.getUserName())
                        .virtualHost(config.getVirtualHost())
                        .build())
                .build();

        final var shovelBodyString = objectMapper.writeValueAsString(shovelBody);

        final var  shovelMessageResponse = sendPutRequest(shovelEndpoint, shovelBodyString, mappedManagementPort);
        Assertions.assertNotNull(shovelMessageResponse);

        // Retry until exactly 3 sideline processor attempts have occurred
        ASSERTION_RETRYER.call(() -> {
            if (actor.getHandleSidelineProcessorCalledCount() != 1) {
                throw new IllegalStateException("Waiting for 1 sideline processor calls");
            }
            return null;
        });
        Assertions.assertEquals(1,actor.getHandleSidelineProcessorCalledCount());

        // Check the number of messages in sideline queue
        assertCountOfMessagesInQueue(sidelineQueue, 1, mappedManagementPort, objectMapper);


        // Check the number of messages in sideline processing queue
        assertCountOfMessagesInQueue(sidelineProcessingQueue, 0, mappedManagementPort, objectMapper);
    }


    private void assertCountOfMessagesInQueue(String queueName, int expectedCount, int mappedManagementPort, ObjectMapper objectMapper) throws IOException {
        final var  endpoint = "/api/queues/%2F/" + queueName + "/get";
        final var  response = sendPostRequest(endpoint, objectMapper.writeValueAsString(getBody()), mappedManagementPort);
        Assertions.assertNotNull(response);

        final var  jsonNode = objectMapper.readTree(response.body()
                .string());
        Assertions.assertEquals(expectedCount, jsonNode.size());
        response.close();
    }

}
