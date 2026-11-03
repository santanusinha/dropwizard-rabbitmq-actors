package io.appform.dropwizard.actors.connectivity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.appform.dropwizard.actors.common.ErrorCode;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import io.appform.dropwizard.actors.config.RmqManagementConfig;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Thin client over the RabbitMQ Management HTTP API, used to read a queue's current bindings (AMQP itself cannot
 * list bindings). Only the read used by the messaging binding drift checks is implemented.
 *
 * <p>Endpoints: {@code managementConfig.host} if set, otherwise every broker host of {@link RMQConfig}, tried
 * in order, starting with the one that answered last. A broker that can't be reached or answers with a server error
 * (5xx) is skipped; an authentication error (401/403) fails immediately, since every node has the same users.
 *
 * <p>Meant for short-lived use (messaging startup): {@link #close()} releases the HTTP client's threads and
 * connections.
 */
@Slf4j
public class RmqManagementClient implements AutoCloseable {

    /**
     * One binding of a queue.
     *
     * @param exchange   the exchange that routes into the queue (the Management API's {@code source} field)
     * @param routingKey the binding key ({@code ""} for FANOUT)
     */
    public record Binding(String exchange, String routingKey) {
    }

    private static final int DEFAULT_PORT = 15672;
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final List<HttpUrl> baseUrls;
    private final String authHeader;
    private volatile boolean closed;
    /**
     * Index of the endpoint that answered last; lookups start there, so an unreachable endpoint costs one timeout per
     * client rather than one per lookup.
     */
    private int preferred;

    public RmqManagementClient(final RMQConfig rmqConfig, final ObjectMapper mapper) {
        this(rmqConfig, mapper, baseUrls(rmqConfig), httpClient(rmqConfig));
    }

    /**
     * Constructor with explicit base URLs, tried in order (e.g. for tests against mock servers).
     */
    public RmqManagementClient(final RMQConfig rmqConfig, final ObjectMapper mapper, final String... baseUrls) {
        this(rmqConfig, mapper, Arrays.asList(baseUrls), httpClient(rmqConfig));
    }

    private RmqManagementClient(final RMQConfig rmqConfig,
                                final ObjectMapper mapper,
                                final List<String> baseUrls,
                                final OkHttpClient httpClient) {
        Preconditions.checkArgument(!baseUrls.isEmpty(), "No Management API endpoint: configure brokers or "
                + "managementConfig.host");
        this.mapper = mapper;
        this.baseUrls = baseUrls.stream()
                .map(url -> Objects.requireNonNull(HttpUrl.parse(url), () -> "Invalid management URL: " + url))
                .collect(Collectors.toList());
        this.authHeader = Credentials.basic(rmqConfig.getUserName(), rmqConfig.getPassword());
        this.httpClient = httpClient;
    }

    /**
     * Reads the bindings of {@code queue} in {@code vhost}: queue-destination bindings only, excluding the implicit
     * default (nameless) exchange binding every queue has.
     *
     * @return the bindings, or {@link Optional#empty()} if the queue does not exist yet (HTTP 404).
     * @throws RabbitmqActorException if no endpoint answers, on an authentication error (401/403), or on any other
     *                                unexpected response.
     * @throws IllegalStateException  if the client is closed.
     */
    public Optional<List<Binding>> fetchBindings(final String vhost, final String queue) {
        Preconditions.checkState(!closed, "Management API client is closed");
        final List<String> failures = new ArrayList<>();
        RabbitmqActorException lastError = null;
        final int start = preferred;
        for (int attempt = 0; attempt < baseUrls.size(); attempt++) {
            final int index = (start + attempt) % baseUrls.size();
            final HttpUrl baseUrl = baseUrls.get(index);
            final HttpUrl url = baseUrl.newBuilder()
                    .addPathSegment("api")
                    .addPathSegment("queues")
                    .addPathSegment(vhost)
                    .addPathSegment(queue)
                    .addPathSegment("bindings")
                    .build();
            try {
                final Optional<List<Binding>> bindings = fetch(url);
                preferred = index;
                return bindings;
            } catch (EndpointUnavailableException e) {
                log.warn("Management API at {} unavailable, trying the next endpoint: {}", baseUrl, e.getMessage());
                failures.add(baseUrl + " (" + e.getMessage() + ")");
                lastError = new RabbitmqActorException(ErrorCode.INTERNAL_ERROR, e.getMessage(), e.getCause());
            }
        }
        throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR,
                "No RabbitMQ Management API endpoint answered: " + failures, lastError);
    }

    /**
     * Releases the HTTP client's threads and pooled connections. Idempotent; {@link #fetchBindings} fails afterwards.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    private static List<String> baseUrls(final RMQConfig rmqConfig) {
        final RmqManagementConfig mgmt = management(rmqConfig);
        final String scheme = mgmt.isSecure() ? "https" : "http";
        final int port = mgmt.getPort() > 0 ? mgmt.getPort() : DEFAULT_PORT;
        final List<String> hosts = !Strings.isNullOrEmpty(mgmt.getHost())
                ? List.of(mgmt.getHost())
                : Objects.requireNonNullElse(rmqConfig.getBrokers(), List.<Broker>of()).stream()
                        .map(Broker::getHost)
                        .distinct()
                        .collect(Collectors.toList());
        return hosts.stream()
                .map(host -> String.format("%s://%s:%d", scheme, host, port))
                .collect(Collectors.toList());
    }

    private static RmqManagementConfig management(final RMQConfig rmqConfig) {
        return Objects.requireNonNullElseGet(rmqConfig.getManagementConfig(),
                () -> RmqManagementConfig.builder().build());
    }

    private static OkHttpClient httpClient(final RMQConfig rmqConfig) {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .callTimeout(CALL_TIMEOUT);
        if (management(rmqConfig).isSecure() && !Strings.isNullOrEmpty(rmqConfig.getServerCertStorePath())) {
            final X509TrustManager trustManager = trustManager(rmqConfig);
            try {
                final SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{trustManager}, null);
                builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
            } catch (GeneralSecurityException e) {
                throw RabbitmqActorException.propagate("Unable to set up TLS for the Management API", e);
            }
        }
        return builder.build();
    }

    /**
     * Trusts the certificates in {@code serverCertStorePath} (JKS), the same trust store as the AMQP connection.
     */
    private static X509TrustManager trustManager(final RMQConfig rmqConfig) {
        try (InputStream in = new FileInputStream(rmqConfig.getServerCertStorePath())) {
            final KeyStore trustStore = KeyStore.getInstance("JKS");
            final String password = rmqConfig.getServerCertPassword();
            trustStore.load(in, password == null ? null : password.toCharArray());
            final TrustManagerFactory factory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trustStore);
            return Arrays.stream(factory.getTrustManagers())
                    .filter(X509TrustManager.class::isInstance)
                    .map(X509TrustManager.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No X509 trust manager for "
                            + rmqConfig.getServerCertStorePath()));
        } catch (IOException | GeneralSecurityException e) {
            throw RabbitmqActorException.propagate(
                    "Unable to load the Management API trust store " + rmqConfig.getServerCertStorePath(), e);
        }
    }

    private Optional<List<Binding>> fetch(final HttpUrl url) {
        final Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Authorization", authHeader)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            final int code = response.code();
            if (code == 404) {
                return Optional.empty();
            }
            if (code == 401 || code == 403) {
                throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR, String.format(
                        "Management API returned %d for %s: the RabbitMQ user needs a management tag "
                                + "(e.g. monitoring) and access to the vhost", code, url), null);
            }
            if (code >= 500) {
                throw new EndpointUnavailableException("HTTP " + code, null);
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new RabbitmqActorException(ErrorCode.INTERNAL_ERROR,
                        String.format("Management API returned %d for %s", code, url), null);
            }
            return Optional.of(parse(response.body().string()));
        } catch (IOException e) {
            throw new EndpointUnavailableException(e.toString(), e);
        }
    }

    private List<Binding> parse(final String body) throws IOException {
        final JsonNode root = mapper.readTree(body);
        final List<Binding> bindings = new ArrayList<>();
        if (root.isArray()) {
            for (final JsonNode node : root) {
                final JsonNode source = node.get("source");
                final JsonNode destinationType = node.get("destination_type");
                if (source == null || destinationType == null || !"queue".equals(destinationType.asText())
                        || source.asText().isEmpty()) {
                    continue;
                }
                final JsonNode routingKey = node.get("routing_key");
                bindings.add(new Binding(source.asText(), routingKey == null ? "" : routingKey.asText()));
            }
        }
        return bindings;
    }

    boolean isClosed() {
        return closed;
    }

    OkHttpClient httpClient() {
        return httpClient;
    }

    /**
     * The endpoint could not be reached or had a server error: the next endpoint is tried.
     */
    private static final class EndpointUnavailableException extends RuntimeException {
        EndpointUnavailableException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
