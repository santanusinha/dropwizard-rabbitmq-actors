package io.appform.dropwizard.actors.messaging.config;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Exchange-centric messaging configuration: exchanges, and the queues bound to each. Validated by
 * {@link MessagingConfigValidator} when {@code Messaging} is created, which fails with the detailed list of errors.
 *
 * <pre>
 * messaging:
 *   prefix: myservice.messaging
 *   exchanges:
 *     order_events:
 *       type: TOPIC
 *       queues:
 *         shipping:
 *           bindingKeys: ["order.*.PLACED"]
 *         audit:
 *           bindingKeys: ["order.#"]
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagingConfig {

    /**
     * Prefix for broker queue names: {@code <namespace>.<prefix>.<queue>}. The default differs from the actors'
     * default prefix, so messaging queues and actor queues don't collide by default. Don't set it to an actor prefix
     * while reusing an actor's queue name. Set one per service (e.g. {@code myservice.messaging}): with the shared
     * default, two services using the same queue name would share one broker queue.
     */
    @NotEmpty
    @Builder.Default
    private String prefix = "rabbitmq.messaging";

    /**
     * Skips the binding drift checks (default {@code false}: the checks run). An opt-out, so a missing or empty value
     * keeps the checks on; only an explicit {@code true} turns them off.
     *
     * <p>The checks: at startup each queue's bindings are read through the RabbitMQ Management API, and startup fails on
     * drift (changed binding keys, a binding to another exchange, or a concurrent creator with a different config).
     * They require the {@code rabbitmq_management} plugin, network access to its port and a RabbitMQ user with a
     * management tag (e.g. {@code monitoring}); startup fails if the API can't be read. Connection settings are in
     * {@code RMQConfig}'s {@code managementConfig}.
     *
     * <p>Set to {@code true} only when the Management API is not available: RabbitMQ never removes a binding by
     * itself, so a changed {@code bindingKeys} or a queue moved to another exchange then keeps its old bindings, and
     * the queue keeps receiving messages it should not, without any error.
     */
    private boolean skipBindingDriftCheck;

    /**
     * Exchanges, keyed by the name your code uses, e.g. {@code messaging.publisher("order_events", ...)}.
     *
     * <p>The exchange created on RabbitMQ usually has the same name. Only when a namespace is set
     * ({@code NAMESPACE_ENV_NAME} / {@code rmq.actors.namespace}, e.g. in feature environments) is it prefixed with
     * it: {@code order_events} becomes {@code <namespace>.order_events} on the broker. Code always uses the name
     * configured here.
     */
    @NotNull
    @Valid
    @Builder.Default
    private Map<String, ExchangeSpec> exchanges = new LinkedHashMap<>();
}
