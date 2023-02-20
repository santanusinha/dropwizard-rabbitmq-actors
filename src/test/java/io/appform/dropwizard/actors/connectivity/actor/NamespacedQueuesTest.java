package io.appform.dropwizard.actors.connectivity.actor;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.ConnectionRegistry;
import io.appform.dropwizard.actors.TtlConfig;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.base.UnmanagedPublisher;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.connectivity.RMQConnection;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlingFactory;
import io.appform.dropwizard.actors.retry.RetryStrategyFactory;
import io.appform.dropwizard.actors.utils.ActorType;
import io.appform.dropwizard.actors.utils.AsyncOperationHelper;
import io.appform.dropwizard.actors.utils.SidelineTestActor;
import io.appform.dropwizard.actors.utils.TestMessage;
import io.appform.testcontainers.rabbitmq.RabbitMQStatusCheck;
import io.appform.testcontainers.rabbitmq.config.RabbitMQContainerConfiguration;
import io.dropwizard.Configuration;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.testcontainers.containers.GenericContainer;

import javax.validation.Validation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;


@Slf4j
public class NamespacedQueuesTest {

    private static final int RABBITMQ_MANAGEMENT_PORT = 15672;
    private static final String RABBITMQ_DOCKER_IMAGE = "rabbitmq:3.8.34-management";
    private static final String RABBITMQ_USERNAME = "guest";
    private static final String RABBITMQ_PASSWORD = "guest";
    private static final String NAMESPACE_ENV_NAME = "namespace1";

    public static final DropwizardAppExtension<RabbitMQBundleTestAppConfiguration> app =
            new DropwizardAppExtension<>(RabbitMQBundleTestApplication.class);

    @BeforeClass
    @SneakyThrows
    public static void beforeMethod() {
        app.before();
    }

    @AfterClass
    @SneakyThrows
    public static void afterMethod() {
        app.after();
    }

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private RMQConfig config;
    private int mappedManagementPort;

    /**
     * This test does the following:
     * - Sets the FEATURE_ENV_NAME environment variable
     * - Launches a RabbitMQ instance in a Docker container
     * - Launches a dummy Dropwizard app for fetching its Environment
     * - Calls /api/queues on the RabbitMQ instance and verifies the names
     */
    @Test
    public void testQueuesAreNamespacedWhenFeatureEnvIsSet() throws Exception {
        environmentVariables.set("NAMESPACE_ENV_NAME", NAMESPACE_ENV_NAME);
        GenericContainer rabbitMQContainer = rabbitMQContainer();
        mappedManagementPort = rabbitMQContainer.getMappedPort(RABBITMQ_MANAGEMENT_PORT);
        config = getRMQConfig(rabbitMQContainer);

        RMQConnection connection = new RMQConnection("test-conn", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build());
        connection.start();

        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        UnmanagedPublisher publisher = new UnmanagedPublisher<>(
                "publisher-1", actorConfig, connection, null);
        publisher.start();

        ObjectMapper objectMapper = new ObjectMapper();
        Response response = sendRequest("/api/queues");
        if (response != null) {
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.isArray()) {
                for (JsonNode json : jsonNode) {
                    String queueName = json.get("name").asText();
                    Assert.assertTrue(queueName.contains(NAMESPACE_ENV_NAME));
                }
            }
            response.close();
        }
    }

    @Test
    public void testQueuesAreNotNamespacedWhenFeatureEnvNotSet() throws Exception {
        GenericContainer rabbitMQContainer = rabbitMQContainer();
        mappedManagementPort = rabbitMQContainer.getMappedPort(RABBITMQ_MANAGEMENT_PORT);
        config = getRMQConfig(rabbitMQContainer);

        RMQConnection connection = new RMQConnection("test-conn", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), TtlConfig.builder().build());
        connection.start();

        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        UnmanagedPublisher publisher = new UnmanagedPublisher<>(
                "publisher-1", actorConfig, connection, null);
        publisher.start();

        ObjectMapper objectMapper = new ObjectMapper();
        Response response = sendRequest("/api/queues");
        if (response != null) {
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.isArray()) {
                for (JsonNode json : jsonNode) {
                    String queueName = json.get("name").asText();
                    Assert.assertFalse(queueName.contains(NAMESPACE_ENV_NAME));
                }
            }
            response.close();
        }
    }

    @Test
    public void testQueuesAreRemovedAfterTtl() throws Exception {
        GenericContainer rabbitMQContainer = rabbitMQContainer();
        mappedManagementPort = rabbitMQContainer.getMappedPort(RABBITMQ_MANAGEMENT_PORT);
        config = getRMQConfig(rabbitMQContainer);

        TtlConfig ttlConfig = TtlConfig.builder()
                .ttlEnabled(true)
                .ttl(Duration.ofSeconds(5))
                .build();
        RMQConnection connection = new RMQConnection("test-conn", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), ttlConfig);
        connection.start();

        ActorConfig actorConfig = new ActorConfig();
        actorConfig.setExchange("test-exchange-1");
        UnmanagedPublisher publisher = new UnmanagedPublisher<>(
                "publisher-1", actorConfig, connection, null);
        publisher.start();

        Thread.sleep(10000);
        Response response = sendRequest("/api/queues");
        if (response != null) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            Assert.assertTrue(jsonNode.size() == 0);
            response.close();
        }
    }

    @Test
    public void testQueuesSidelineForFailedMessages() throws Exception {
        GenericContainer rabbitMQContainer = rabbitMQContainer();
        mappedManagementPort = rabbitMQContainer.getMappedPort(RABBITMQ_MANAGEMENT_PORT);
        config = getRMQConfig(rabbitMQContainer);

        RMQConnection connection = new RMQConnection("test-conn", config,
                Executors.newSingleThreadExecutor(), app.getEnvironment(), null);
        connection.start();

        ActorConfig actorConfig = AsyncOperationHelper.buildActorConfig();
        Environment environment = new Environment("testing",
                Jackson.newObjectMapper(),
                Validation.buildDefaultValidatorFactory(),
                new MetricRegistry(),
                Thread.currentThread().getContextClassLoader(),
                new HealthCheckRegistry(),
                new Configuration());
        ConnectionRegistry registry = new ConnectionRegistry(environment,
                (name, coreSize) -> Executors.newFixedThreadPool(1),
                config, TtlConfig.builder().build());
        ObjectMapper mapper = new ObjectMapper();
        SidelineTestActor actor = new SidelineTestActor(actorConfig, registry, mapper,
                new RetryStrategyFactory(), new ExceptionHandlingFactory());
        actor.start();
        TestMessage message = TestMessage.builder()
                .actorType(ActorType.ALWAYS_FAIL_ACTOR)
                .name("test_message")
                .build();
        actor.publish(message);

        Thread.sleep(10000);
        String sidelineQueue = "test.ALWAYS_FAIL_ACTOR_SIDELINE";
        String endpoint = "/api/queues/%2F/" + sidelineQueue + "/get";

        Response response = sendPostRequest(endpoint, mapper.writeValueAsString(getBody()));
        if (response != null) {
            ObjectMapper objectMapper = mapper;
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            Assert.assertEquals( 1, jsonNode.size());
            Assert.assertEquals("test.exchange_SIDELINE", jsonNode.get(0).get("exchange").asText());
            TestMessage actualMessage = objectMapper.readValue(jsonNode.get(0).get("payload").asText(), TestMessage.class);
            Assert.assertEquals(ActorType.ALWAYS_FAIL_ACTOR, actualMessage.getActorType());
            Assert.assertEquals("test_message", actualMessage.getName());
            response.close();
        }
    }

    private RMQFetchMessages getBody() {
        return new RMQFetchMessages();
    }

    private Response sendRequest(String endpoint) {
        OkHttpClient client = new OkHttpClient();
        String credential = Credentials.basic(config.getUserName(), config.getPassword());
        Request request = new Request.Builder()
                .url("http://" + config.getBrokers().get(0).getHost() + ":" + mappedManagementPort + endpoint)
                .header("Accept", "application/json")
                .header("Authorization", credential)
                .build();

        try {
            return client.newCall(request).execute();
        } catch (IOException e) {
            log.error("Error while making API call to RabbitMQ");
        }
        return null;
    }

    private Response sendPostRequest(String endpoint, String body) {
        OkHttpClient client = new OkHttpClient();
        String credential = Credentials.basic(config.getUserName(), config.getPassword());
        Request request = new Request.Builder()
                .url("http://" + config.getBrokers().get(0).getHost() + ":" + mappedManagementPort + endpoint)
                .header("Accept", "application/json")
                .header("Authorization", credential)
                .post(RequestBody.create(body.getBytes(StandardCharsets.UTF_8)))
                .build();

        try {
            return client.newCall(request).execute();
        } catch (IOException e) {
            log.error("Error while making API call to RabbitMQ");
        }
        return null;
    }

    private static GenericContainer rabbitMQContainer() {
        RabbitMQContainerConfiguration containerConfiguration = new RabbitMQContainerConfiguration();
        log.info("Starting rabbitMQ server. Docker image: {}", containerConfiguration.getDockerImage());

        GenericContainer rabbitMQ =
                new GenericContainer(RABBITMQ_DOCKER_IMAGE)
                        .withEnv("RABBITMQ_DEFAULT_VHOST", containerConfiguration.getVhost())
                        .withEnv("RABBITMQ_DEFAULT_USER", RABBITMQ_USERNAME)
                        .withEnv("RABBITMQ_DEFAULT_PASS", RABBITMQ_PASSWORD)
                        .withExposedPorts(containerConfiguration.getPort(), RABBITMQ_MANAGEMENT_PORT)
                        .waitingFor(new RabbitMQStatusCheck(containerConfiguration))
                        .withStartupTimeout(Duration.ofSeconds(30));

        rabbitMQ = rabbitMQ.withStartupCheckStrategy(new IsRunningStartupCheckStrategyWithDelay());
        rabbitMQ.start();
        log.info("Started RabbitMQ server");
        return rabbitMQ;
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
