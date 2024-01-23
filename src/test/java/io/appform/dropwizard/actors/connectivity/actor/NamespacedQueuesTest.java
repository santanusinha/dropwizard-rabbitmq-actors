package io.appform.dropwizard.actors.connectivity.actor;

import static io.appform.dropwizard.actors.utils.RMQContainer.RABBITMQ_MANAGEMENT_PORT;
import static io.appform.dropwizard.actors.utils.RMQContainer.RABBITMQ_PASSWORD;
import static io.appform.dropwizard.actors.utils.RMQContainer.RABBITMQ_USERNAME;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.base.utils.NamingUtils;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.metrics.RMQMetricObserver;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.utils.ActorType;
import io.appform.dropwizard.actors.utils.AsyncOperationHelper;
import io.appform.dropwizard.actors.utils.RMQContainer;
import io.appform.dropwizard.actors.utils.SidelineTestActor;
import io.appform.dropwizard.actors.utils.TestMessage;
import io.dropwizard.Configuration;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;


@Slf4j
public class NamespacedQueuesTest {

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

    private static RabbitMQContainer rabbitMQContainer() {
        return RMQContainer.startContainer();
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
    public void testQueuesAreNamespacedWhenFeatureEnvIsSet() throws Exception {
        String queueName = "publisher-0";
        RabbitMQContainer rabbitMQContainer = rabbitMQContainer();
        val mappedManagementPort = rabbitMQContainer.getMappedPort(RABBITMQ_MANAGEMENT_PORT);
        config = getRMQConfig(rabbitMQContainer);

        RMQConnection connection = new RMQConnection("test-conn-0", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));
        connection.start();

        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-0");
        UnmanagedPublisher publisher = new UnmanagedPublisher<>(queueName, actorConfig, connection, null);
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
    public void testQueuesAreNotNamespacedWhenFeatureEnvNotSet() throws Exception {
        String queueName = "publisher-1";
        System.clearProperty(NamingUtils.NAMESPACE_PROPERTY_NAME);
        RabbitMQContainer rabbitMQContainer = rabbitMQContainer();
        val mappedManagementPort = rabbitMQContainer.getMappedPort(RABBITMQ_MANAGEMENT_PORT);
        config = getRMQConfig(rabbitMQContainer);

        RMQConnection connection = new RMQConnection("test-conn-1", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build(), new RMQMetricObserver(config, metricRegistry));
        connection.start();

        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        UnmanagedPublisher publisher = new UnmanagedPublisher<>(queueName, actorConfig, connection, null);
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
    public void testQueuesAreRemovedAfterTtl() throws Exception {
        String queueName = "publisher-2";
        RabbitMQContainer rabbitMQContainer = rabbitMQContainer();
        val mappedManagementPort = rabbitMQContainer.getMappedPort(RABBITMQ_MANAGEMENT_PORT);
        config = getRMQConfig(rabbitMQContainer);

        TtlConfig ttlConfig = TtlConfig.builder()
                .ttlEnabled(true)
                .ttl(Duration.ofSeconds(2))
                .build();
        RMQConnection connection = new RMQConnection("test-conn-2", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), ttlConfig, new RMQMetricObserver(config, metricRegistry));
        connection.start();

        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-2");
        UnmanagedPublisher publisher = new UnmanagedPublisher<>(queueName, actorConfig, connection, null);
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

    @Test
    public void testQueuesSidelineForFailedMessages() throws Exception {
        RabbitMQContainer rabbitMQContainer = rabbitMQContainer();
        val mappedManagementPort = rabbitMQContainer.getMappedPort(RABBITMQ_MANAGEMENT_PORT);
        config = getRMQConfig(rabbitMQContainer);

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

    private static RMQConfig getRMQConfig(GenericContainer rabbitmqContainer) {
        RMQConfig rmqConfig = new RMQConfig();
        Integer mappedPort = rabbitmqContainer.getMappedPort(5672);
        String host = rabbitmqContainer.getContainerIpAddress();
        List<Broker> brokers = new ArrayList<Broker>();
        brokers.add(new Broker(host, mappedPort));
        rmqConfig.setBrokers(brokers);
        rmqConfig.setUserName(RABBITMQ_USERNAME);
        rmqConfig.setPassword(RABBITMQ_PASSWORD);
        rmqConfig.setVirtualHost("/");
        log.info("RabbitMQ connection details: {}", rmqConfig);
        return rmqConfig;
    }

}
