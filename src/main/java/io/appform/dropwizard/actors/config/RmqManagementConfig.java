package io.appform.dropwizard.actors.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * Connection settings for the RabbitMQ Management HTTP API. Optional: without it, the defaults below are used.
 *
 * <p>AMQP has no operation to list a queue's bindings, so the messaging binding drift checks (on by default, see
 * {@code MessagingConfig.skipBindingDriftCheck}) read them through the Management API (requires the
 * {@code rabbitmq_management} plugin). Whether the API is called at all is decided by that messaging setting, not by
 * this config.
 *
 * <p>Connection details:
 * <ul>
 *   <li>host: {@link #host} if set (e.g. a load balancer in front of the management API); otherwise each broker in
 *       {@link RMQConfig}{@code .brokers}, tried in order until one answers;</li>
 *   <li>credentials: the AMQP username/password. The user needs a management tag (e.g. {@code monitoring}) to
 *       read bindings;</li>
 *   <li>{@link #secure}: switches to https. The server certificate is verified against
 *       {@link RMQConfig}{@code .serverCertStorePath} when set, otherwise against the JVM's default trust store.</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RmqManagementConfig {
    /**
     * Management API host. When not set, the brokers' hosts are tried in order.
     */
    private String host;
    /**
     * Management API port (RabbitMQ default is 15672).
     */
    @Default
    @Min(1)
    @Max(65535)
    private int port = 15672;
    /**
     * Use https for the Management API endpoint.
     */
    @Default
    private boolean secure = false;
}
