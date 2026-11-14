package io.appform.dropwizard.actors.connectivity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.config.RmqManagementConfig;
import io.appform.dropwizard.actors.connectivity.RmqManagementClient.Binding;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RmqManagementClientTest {

    private MockWebServer server;
    private RmqManagementClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        final RMQConfig rmqConfig = new RMQConfig();
        final List<Broker> brokers = new ArrayList<>();
        brokers.add(new Broker("localhost", 5672));
        rmqConfig.setBrokers(brokers);
        rmqConfig.setUserName("guest");
        rmqConfig.setPassword("guest");
        rmqConfig.setVirtualHost("/");

        client = new RmqManagementClient(rmqConfig, new ObjectMapper(), server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void testReturnsQueueBindingsExcludingDefaultExchangeAndExchangeDestinations() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "[{\"source\":\"\",\"destination_type\":\"queue\",\"routing_key\":\"q\"},"
                        + "{\"source\":\"ex\",\"destination_type\":\"queue\",\"routing_key\":\"order.*\"},"
                        + "{\"source\":\"ex\",\"destination_type\":\"queue\",\"routing_key\":\"payment.created\"},"
                        + "{\"source\":\"other\",\"destination_type\":\"queue\",\"routing_key\":\"x\"},"
                        + "{\"source\":\"ex\",\"destination_type\":\"exchange\",\"routing_key\":\"e2e\"}]"));

        final Optional<List<Binding>> bindings = client.fetchBindings("/", "q");

        Assertions.assertEquals(Optional.of(List.of(
                new Binding("ex", "order.*"),
                new Binding("ex", "payment.created"),
                new Binding("other", "x"))), bindings);
        final RecordedRequest recorded = server.takeRequest();
        // vhost "/" is a single encoded path segment
        Assertions.assertEquals("/api/queues/%2F/q/bindings", recorded.getPath());
        Assertions.assertNotNull(recorded.getHeader("Authorization"));
    }

    @Test
    void testQueueNameIsEncodedAsPathSegment() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        client.fetchBindings("/", "ns.my queue");

        Assertions.assertEquals("/api/queues/%2F/ns.my%20queue/bindings", server.takeRequest().getPath());
    }

    @Test
    void test404ReturnsEmpty() {
        server.enqueue(new MockResponse().setResponseCode(404));
        Assertions.assertTrue(client.fetchBindings("/", "q").isEmpty());
    }

    @Test
    void testNon200ThrowsExceptions() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        Assertions.assertThrows(RabbitmqActorException.class, () -> client.fetchBindings("/", "q"));
    }

    @Test
    void testEmptyBindingsList() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        Assertions.assertEquals(Optional.of(List.of()), client.fetchBindings("/", "q"));
    }

    @Test
    void testMissingRoutingKeyIsEmpty() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "[{\"source\":\"fan\",\"destination_type\":\"queue\"}]"));
        Assertions.assertEquals(Optional.of(List.of(new Binding("fan", ""))), client.fetchBindings("/", "q"));
    }

    private RMQConfig config(final RmqManagementConfig management, final String... brokerHosts) {
        final RMQConfig rmqConfig = new RMQConfig();
        final List<Broker> brokers = new ArrayList<>();
        for (final String host : brokerHosts) {
            brokers.add(new Broker(host, 5672));
        }
        rmqConfig.setBrokers(brokers);
        rmqConfig.setUserName("guest");
        rmqConfig.setPassword("guest");
        rmqConfig.setManagementConfig(management);
        return rmqConfig;
    }

    @Test
    void testTriesNextEndpointWhenOneIsUnreachable() throws Exception {
        final MockWebServer down = new MockWebServer();
        down.start();
        final String downUrl = down.url("/").toString();
        down.shutdown();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        final RmqManagementClient fallback = new RmqManagementClient(config(null, "localhost"), new ObjectMapper(),
                downUrl, server.url("/").toString());

        Assertions.assertEquals(Optional.of(List.of()), fallback.fetchBindings("/", "q"));
    }

    @Test
    void testTriesNextEndpointOnServerError() {
        final MockWebServer second = new MockWebServer();
        try {
            second.start();
            server.enqueue(new MockResponse().setResponseCode(503));
            second.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
            final RmqManagementClient fallback = new RmqManagementClient(config(null, "localhost"),
                    new ObjectMapper(), server.url("/").toString(), second.url("/").toString());

            Assertions.assertEquals(Optional.of(List.of()), fallback.fetchBindings("/", "q"));
            second.shutdown();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void testAuthenticationErrorFailsWithoutTryingOtherEndpoints() throws Exception {
        final MockWebServer second = new MockWebServer();
        second.start();
        server.enqueue(new MockResponse().setResponseCode(401));
        final RmqManagementClient twoEndpoints = new RmqManagementClient(config(null, "localhost"),
                new ObjectMapper(), server.url("/").toString(), second.url("/").toString());

        final RabbitmqActorException e = Assertions.assertThrows(RabbitmqActorException.class,
                () -> twoEndpoints.fetchBindings("/", "q"));
        Assertions.assertTrue(e.getMessage().contains("management tag"), e.getMessage());
        Assertions.assertEquals(0, second.getRequestCount());
        second.shutdown();
    }

    @Test
    void testAllEndpointsDownFails() {
        server.enqueue(new MockResponse().setResponseCode(500));
        final RabbitmqActorException e = Assertions.assertThrows(RabbitmqActorException.class,
                () -> client.fetchBindings("/", "q"));
        Assertions.assertTrue(e.getMessage().contains("No RabbitMQ Management API endpoint answered"), e.getMessage());
    }

    @Test
    void testConfiguredHostIsUsedInsteadOfBrokers() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        final RmqManagementConfig management = RmqManagementConfig.builder()
                .host(server.getHostName())
                .port(server.getPort())
                .build();
        final RmqManagementClient configured = new RmqManagementClient(config(management, "broker.invalid"),
                new ObjectMapper());

        Assertions.assertEquals(Optional.of(List.of()), configured.fetchBindings("/", "q"));
        Assertions.assertEquals(1, server.getRequestCount());
    }

    @Test
    void testBrokersAreTriedInOrder() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        final RmqManagementConfig management = RmqManagementConfig.builder().port(server.getPort()).build();
        final RmqManagementClient brokers = new RmqManagementClient(
                config(management, "broker.invalid", server.getHostName()), new ObjectMapper());

        Assertions.assertEquals(Optional.of(List.of()), brokers.fetchBindings("/", "q"));
    }

    @Test
    void testCloseReleasesTheClient() {
        client.close();
        client.close();

        Assertions.assertTrue(client.isClosed());
        Assertions.assertThrows(IllegalStateException.class, () -> client.fetchBindings("/", "q"));
    }

    @Test
    void testNoEndpointFailsConstruction() {
        final RMQConfig noEndpoint = config(null);
        final ObjectMapper mapper = new ObjectMapper();
        Assertions.assertThrows(IllegalArgumentException.class, () -> new RmqManagementClient(noEndpoint, mapper));
    }

    @Test
    void testSecureUsesServerCertStoreAsTrustStore(@TempDir Path dir)
            throws Exception {
        final Path trustStore = dir.resolve("server.jks");
        final KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        try (OutputStream out = Files.newOutputStream(trustStore)) {
            keyStore.store(out, "secret".toCharArray());
        }
        final RMQConfig rmqConfig = config(RmqManagementConfig.builder().secure(true).build(), "localhost");
        rmqConfig.setServerCertStorePath(trustStore.toString());
        rmqConfig.setServerCertPassword("secret");

        new RmqManagementClient(rmqConfig, new ObjectMapper()).close();

        rmqConfig.setServerCertStorePath(dir.resolve("missing.jks").toString());
        final ObjectMapper mapper = new ObjectMapper();
        final RabbitmqActorException e = Assertions.assertThrows(RabbitmqActorException.class,
                () -> new RmqManagementClient(rmqConfig, mapper));
        Assertions.assertTrue(e.getMessage().contains("trust store"), e.getMessage());
    }

    @Test
    void testStartsWithTheEndpointThatAnsweredLast() throws Exception {
        final MockWebServer first = new MockWebServer();
        first.start();
        first.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        final RmqManagementClient sticky = new RmqManagementClient(config(null, "localhost"), new ObjectMapper(),
                first.url("/").toString(), server.url("/").toString());

        sticky.fetchBindings("/", "q1");
        sticky.fetchBindings("/", "q2");

        // The failing endpoint is tried once; the second lookup goes straight to the one that answered.
        Assertions.assertEquals(1, first.getRequestCount());
        Assertions.assertEquals(2, server.getRequestCount());
        first.shutdown();
    }

    @Test
    void testTimeouts() {
        final okhttp3.OkHttpClient http = client.httpClient();
        Assertions.assertEquals(5_000, http.connectTimeoutMillis());
        Assertions.assertEquals(10_000, http.readTimeoutMillis());
        Assertions.assertEquals(15_000, http.callTimeoutMillis());
    }
}
