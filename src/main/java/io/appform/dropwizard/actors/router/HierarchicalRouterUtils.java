package io.appform.dropwizard.actors.router;

import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.ProducerConfig;
import io.appform.dropwizard.actors.router.tree.key.RoutingKey;
import io.appform.dropwizard.actors.connectivity.strategy.ConnectionIsolationStrategyVisitor;
import io.appform.dropwizard.actors.connectivity.strategy.DefaultConnectionStrategy;
import io.appform.dropwizard.actors.connectivity.strategy.SharedConnectionStrategy;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public class HierarchicalRouterUtils {

    private static final String EXCHANGES = "exchanges";
    private static final String ACTORS = "actors";

    private static final BiFunction<Stream<String>, String, String> beautifierFunction = (stream, delimiter) -> stream
            .filter(e -> !StringUtils.isEmpty(e))
            .map(String::toLowerCase)
            .collect(Collectors.joining(delimiter));

    public static <MessageType extends Enum<MessageType>> ActorConfig toActorConfig(final MessageType messageType,
                                                                                    final RoutingKey routingKeyData,
                                                                                    final ActorConfig actorConfig) {
        actorConfig.setExchange(exchangeName(actorConfig.getExchange(), messageType, routingKeyData));
        actorConfig.setPrefix(prefix(actorConfig.getPrefix(), routingKeyData));
        consumerIsolationStrategyName(actorConfig.getConsumer(), routingKeyData);
        producerIsolationStrategyName(actorConfig.getProducer(), routingKeyData);

        return actorConfig;
    }

    public static <MessageType extends Enum<MessageType>> String exchangeName(final String providedExchangeName,
                                                                               final MessageType messageType,
                                                                               final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();
        if (!StringUtils.isEmpty(providedExchangeName)) {
            return beautifierFunction.apply(Stream.of(providedExchangeName, String.join(".", routingKey), messageType.name()), ".");
        }
        return beautifierFunction.apply(Stream.of(EXCHANGES, String.join(".", routingKey), messageType.name()), ".");
    }

    private static String prefix(final String providedPrefixName,
                                 final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();
        if (!StringUtils.isEmpty(providedPrefixName)) {
            return beautifierFunction.apply(Stream.of(providedPrefixName, String.join(".", routingKey)), ".");
        }
        return beautifierFunction.apply(Stream.of(ACTORS, String.join(".", routingKey)), ".");
    }


    private static void producerIsolationStrategyName(final ProducerConfig producerConfig,
                                                      final RoutingKey routingKeyData) {
        if (Objects.isNull(producerConfig) || Objects.isNull(producerConfig.getConnectionIsolationStrategy())) {
            return;
        }
        producerConfig.getConnectionIsolationStrategy().accept(updateIsolationStrategyConfig(routingKeyData.getRoutingKey()));
    }

    private static void consumerIsolationStrategyName(final ConsumerConfig consumerConfig,
                                                      final RoutingKey routingKeyData) {
        if (Objects.isNull(consumerConfig) || Objects.isNull(consumerConfig.getConnectionIsolationStrategy())) {
            return;
        }
        consumerConfig.getConnectionIsolationStrategy().accept(updateIsolationStrategyConfig(routingKeyData.getRoutingKey()));
    }


    private static ConnectionIsolationStrategyVisitor<Void> updateIsolationStrategyConfig(final List<String> routingKeyData) {
        return new ConnectionIsolationStrategyVisitor<Void>() {
            @Override
            public Void visit(SharedConnectionStrategy strategy) {
                val producerIsolationStrategyName = beautifierFunction.apply(Stream.of("p",
                        strategy.getName(), String.join("_", routingKeyData)), "_");
                strategy.setName(producerIsolationStrategyName);
                return null;
            }

            @Override
            public Void visit(DefaultConnectionStrategy strategy) {
                return null;
            }
        };
    }
}
