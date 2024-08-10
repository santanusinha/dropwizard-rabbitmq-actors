package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.ProducerConfig;
import io.appform.dropwizard.actors.connectivity.strategy.ConnectionIsolationStrategy;
import io.appform.dropwizard.actors.connectivity.strategy.ConnectionIsolationStrategyVisitor;
import io.appform.dropwizard.actors.connectivity.strategy.DefaultConnectionStrategy;
import io.appform.dropwizard.actors.connectivity.strategy.SharedConnectionStrategy;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.RoutingKey;
import io.appform.dropwizard.actors.utils.MapperUtils;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public class HierarchicalRouterUtils {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String EXCHANGES = "exchanges";
    private static final String ACTORS = "actors";

    private static final BiFunction<Stream<String>, String, String> beautifierFunction = (stream, delimiter) -> stream
            .filter(e -> !StringUtils.isEmpty(e))
            .collect(Collectors.joining(delimiter));

    public static final Function<ActorConfig, HierarchicalOperationWorkerConfig> actorConfigToWorkerConfigFunc =
            actorConfig -> HierarchicalOperationWorkerConfig.builder()
                    .concurrency(actorConfig.getConcurrency())
                    .shardCount(actorConfig.getShardCount())
                    .prefetchCount(actorConfig.getPrefetchCount())
                    .consumer(actorConfig.getConsumer())
                    .producer(actorConfig.getProducer())
                    .build();

    @SneakyThrows
    public static <MessageType extends Enum<MessageType>> ActorConfig toActorConfig(final MessageType messageType,
                                                                                    final RoutingKey routingKeyData,
                                                                                    final HierarchicalOperationWorkerConfig workerConfig,
                                                                                    final ActorConfig actorConfig) {
        return ActorConfig.builder()
                // Custom fields
                .exchange(exchangeName(actorConfig.getExchange(), messageType, routingKeyData))
                .prefix(prefix(actorConfig.getPrefix(), routingKeyData))
                .concurrency(workerConfig.getConcurrency())
                .shardCount(Objects.nonNull(workerConfig.getShardCount()) && workerConfig.getShardCount() >= 1 ? workerConfig.getShardCount() : null)
                .consumer(consumerConfig(workerConfig.getConsumer(), actorConfig.getConsumer(), routingKeyData))
                .producer(producerConfig(workerConfig.getProducer(), actorConfig.getProducer(), routingKeyData))
                // Copy parent data
                .delayed(actorConfig.isDelayed())
                .priorityQueue(actorConfig.isPriorityQueue())
                .maxPriority(actorConfig.getMaxPriority())
                .delayType(actorConfig.getDelayType())
                .queueType(actorConfig.getQueueType())
                .haMode(actorConfig.getHaMode())
                .haParams(actorConfig.getHaParams())
                .lazyMode(actorConfig.isLazyMode())
                .quorumInitialGroupSize(actorConfig.getQuorumInitialGroupSize())
                .retryConfig(actorConfig.getRetryConfig())
                .exceptionHandlerConfig(actorConfig.getExceptionHandlerConfig())
                .ttlConfig(actorConfig.getTtlConfig())
                .build();
    }

    public static <MessageType extends Enum<MessageType>> String exchangeName(final String defaultExchangeName,
                                                                              final MessageType messageType,
                                                                              final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();

        if (!StringUtils.isEmpty(defaultExchangeName)) {
            // For backward compatibility
            if(routingKey.isEmpty()) {
                return defaultExchangeName;
            }

            return beautifierFunction.apply(Stream.of(defaultExchangeName, String.join(".", routingKey)), ".");
        }

        return beautifierFunction.apply(Stream.of(EXCHANGES, String.join(".", routingKey), messageType.name()), ".");
    }

    private static <MessageType extends Enum<MessageType>> String prefix(final String defaultPrefixName,
                                                                         final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();
        if (!StringUtils.isEmpty(defaultPrefixName)) {
            return beautifierFunction.apply(Stream.of(defaultPrefixName, String.join(".", routingKey)), ".");
        }

        return beautifierFunction.apply(Stream.of(ACTORS, String.join(".", routingKey)), ".");
    }


    private static ProducerConfig producerConfig(final ProducerConfig workerConfig,
                                                 final ProducerConfig defaultProducerConfig,
                                                 final RoutingKey routingKeyData) {
        if (Objects.isNull(workerConfig)) {
            return MapperUtils.clone(defaultProducerConfig, ProducerConfig.class);
        }

        if (Objects.isNull(workerConfig.getConnectionIsolationStrategy())) {
            val isolationStrtegy = MapperUtils.clone(defaultProducerConfig.getConnectionIsolationStrategy(), ConnectionIsolationStrategy.class);
            workerConfig.setConnectionIsolationStrategy(isolationStrtegy);
        }
        updateIsolationStrategyConfig(workerConfig.getConnectionIsolationStrategy(), routingKeyData.getRoutingKey());
        return workerConfig;
    }

    private static ConsumerConfig consumerConfig(final ConsumerConfig workerConfig,
                                                 final ConsumerConfig defaultConsumerConfig,
                                                 final RoutingKey routingKeyData) {
        if (Objects.isNull(workerConfig)) {
            return MapperUtils.clone(defaultConsumerConfig, ConsumerConfig.class);
        }

        if (Objects.isNull(workerConfig.getConnectionIsolationStrategy())) {
            val isolationStrtegy = MapperUtils.clone(defaultConsumerConfig.getConnectionIsolationStrategy(), ConnectionIsolationStrategy.class);
            workerConfig.setConnectionIsolationStrategy(isolationStrtegy);
        }
        updateIsolationStrategyConfig(workerConfig.getConnectionIsolationStrategy(), routingKeyData.getRoutingKey());
        return workerConfig;
    }


    private static void updateIsolationStrategyConfig(final ConnectionIsolationStrategy isolationStrategy,
                                                      final List<String> routingKeyData) {
        isolationStrategy.accept(new ConnectionIsolationStrategyVisitor<Void>() {
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
        });
    }

}
