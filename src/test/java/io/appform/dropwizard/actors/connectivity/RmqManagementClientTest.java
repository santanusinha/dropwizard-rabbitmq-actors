package io.appform.dropwizard.actors.connectivity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

        final String baseUrl = server.url("/").toString();
        client = new RmqManagementClient(rmqConfig, new ObjectMapper(), baseUrl);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void testReturnsMatchingRoutingKeys() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "[{\"source\":\"ex\",\"destination_type\":\"queue\",\"routing_key\":\"order.*\"},"
                        + "{\"source\":\"ex\",\"destination_type\":\"queue\",\"routing_key\":\"payment.created\"},"
                        + "{\"source\":\"other\",\"destination_type\":\"queue\",\"routing_key\":\"ignore.me\"},"
                        + "{\"source\":\"ex\",\"destination_type\":\"exchange\",\"routing_key\":\"e2e\"}]"));

        Optional<List<String>> keys = client.fetchBindingKeys("/", "ex", "q");
        Assertions.assertTrue(keys.isPresent());
        Assertions.assertEquals(java.util.Set.of("order.*", "payment.created"),
                new java.util.HashSet<>(keys.get()));

        // Verify URL encoding: vhost "/" -> %2F, path includes /api/queues/.../bindings
        RecordedRequest recorded = server.takeRequest();
        Assertions.assertEquals("/api/queues/%2F/q/bindings", recorded.getPath());
        Assertions.assertNotNull(recorded.getHeader("Authorization"));
    }

    @Test
    void test404ReturnsEmpty() {
        server.enqueue(new MockResponse().setResponseCode(404));
        Optional<List<String>> keys = client.fetchBindingKeys("/", "ex", "q");
        Assertions.assertTrue(keys.isEmpty());
    }

    @Test
    void testNon200ThrowsExceptions() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        Assertions.assertThrows(RabbitmqActorException.class,
                () -> client.fetchBindingKeys("/", "ex", "q"));
    }

    @Test
    void testEmptyBindingsList() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        Optional<List<String>> keys = client.fetchBindingKeys("/", "ex", "q");
        Assertions.assertTrue(keys.isPresent());
        Assertions.assertTrue(keys.get().isEmpty());
    }

    @Test
    void testFetchSourceExchangesExcludesDefaultExchange() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "[{\"source\":\"\",\"destination_type\":\"queue\",\"routing_key\":\"q\"},"
                        + "{\"source\":\"ex_new\",\"destination_type\":\"queue\",\"routing_key\":\"\"},"
                        + "{\"source\":\"ex_stale\",\"destination_type\":\"queue\",\"routing_key\":\"\"},"
                        + "{\"source\":\"ex_new\",\"destination_type\":\"exchange\",\"routing_key\":\"e2e\"}]"));

        Optional<java.util.Set<String>> exchanges = client.fetchSourceExchanges("/", "q");
        Assertions.assertTrue(exchanges.isPresent());
        // Default exchange ("") excluded; exchange->exchange binding ignored; only queue source exchanges kept.
        Assertions.assertEquals(java.util.Set.of("ex_new", "ex_stale"), exchanges.get());
    }

    @Test
    void testFetchSourceExchanges404ReturnsEmpty() {
        server.enqueue(new MockResponse().setResponseCode(404));
        Assertions.assertTrue(client.fetchSourceExchanges("/", "q").isEmpty());
    }
}
