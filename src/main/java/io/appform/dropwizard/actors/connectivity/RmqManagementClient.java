package io.appform.dropwizard.actors.connectivity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.common.ErrorCode;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.config.RmqManagementConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Thin client over the RabbitMQ Management HTTP API, used to read a queue's current binding set (AMQP
 * itself cannot list bindings). Only the read used by the immutable {@code bindingKeys} check is
 * implemented.
 *
 * <p>Host and credentials are inherited from {@link RMQConfig} (first broker host, AMQP username /
 * password); port/scheme come from {@link RmqManagementConfig}.
 */
@Slf4j
public class RmqManagementClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String authHeader;

    public RmqManagementClient(final RMQConfig rmqConfig, final ObjectMapper mapper) {
        this(rmqConfig, mapper, defaultBaseUrl(rmqConfig));
    }

    /**
     * Constructor allowing an explicit base URL (e.g. for tests against a mock server).
     */
    RmqManagementClient(final RMQConfig rmqConfig, final ObjectMapper mapper, final String baseUrl) {
        this.mapper = mapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.authHeader = Credentials.basic(rmqConfig.getUserName(), rmqConfig.getPassword());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    private static String defaultBaseUrl(final RMQConfig rmqConfig) {
        final RmqManagementConfig mgmt = rmqConfig.getManagementConfig();
        final String scheme = mgmt != null && mgmt.isSecure() ? "https" : "http";
        final String host = rmqConfig.getBrokers().get(0).getHost();
        final int port = mgmt != null ? mgmt.getPort() : 15672;
        return String.format("%s://%s:%d", scheme, host, port);
    }

    /**
     * Reads the routing keys of all bindings from {@code exchange} to {@code queue} in {@code vhost}.
     *
     * @return the list of routing keys, or {@link Optional#empty()} if the queue does not exist yet
     *         (HTTP 404) - the first-creation signal.
     * @throws RabbitmqActorException on any transport error or non-200/404 response.
     */
    public Optional<List<String>> fetchBindingKeys(final String vhost,
                                                   final String exchange,
                                                   final String queue) {
        final String url = String.format("%s/api/queues/%s/%s/bindings",
                baseUrl, encode(vhost), encode(queue));
        final Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Authorization", authHeader)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                return Optional.empty();
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR,
                        String.format("Management API returned %d for %s", response.code(), url), null);
            }
            final JsonNode root = mapper.readTree(response.body().string());
            final List<String> keys = new ArrayList<>();
            if (root.isArray()) {
                for (final JsonNode node : root) {
                    // Only bindings from THIS exchange to a queue destination are relevant.
                    final JsonNode source = node.get("source");
                    final JsonNode destinationType = node.get("destination_type");
                    if (source != null && exchange.equals(source.asText())
                            && destinationType != null && "queue".equals(destinationType.asText())) {
                        final JsonNode routingKey = node.get("routing_key");
                        keys.add(routingKey == null ? "" : routingKey.asText());
                    }
                }
            }
            return Optional.of(keys);
        } catch (IOException e) {
            throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR,
                    "Error calling RabbitMQ Management API: " + url, e);
        }
    }

    /**
     * Reads the distinct source exchange names this queue is bound to (destination_type == queue),
     * excluding the implicit default (nameless) exchange whose source is the empty string.
     *
     * @return the set of source exchange names, or {@link Optional#empty()} if the queue does not exist
     *         yet (HTTP 404).
     * @throws RabbitmqActorException on any transport error or non-200/404 response.
     */
    public Optional<Set<String>> fetchSourceExchanges(final String vhost, final String queue) {
        final String url = String.format("%s/api/queues/%s/%s/bindings",
                baseUrl, encode(vhost), encode(queue));
        final Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Authorization", authHeader)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                return Optional.empty();
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR,
                        String.format("Management API returned %d for %s", response.code(), url), null);
            }
            final JsonNode root = mapper.readTree(response.body().string());
            final Set<String> exchanges = new HashSet<>();
            if (root.isArray()) {
                for (final JsonNode node : root) {
                    final JsonNode source = node.get("source");
                    final JsonNode destinationType = node.get("destination_type");
                    if (source != null && destinationType != null
                            && "queue".equals(destinationType.asText())) {
                        final String sourceExchange = source.asText();
                        // Exclude the implicit default (nameless) exchange binding every queue has.
                        if (!sourceExchange.isEmpty()) {
                            exchanges.add(sourceExchange);
                        }
                    }
                }
            }
            return Optional.of(exchanges);
        } catch (IOException e) {
            throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR,
                    "Error calling RabbitMQ Management API: " + url, e);
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(final String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
