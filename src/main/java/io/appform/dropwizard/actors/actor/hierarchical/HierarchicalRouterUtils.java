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

    public static final Function<HierarchicalActorConfig, HierarchicalOperationWorkerConfig> actorConfigToWorkerConfigFunc =
            actorConfig -> HierarchicalOperationWorkerConfig.builder()
                    .concurrency(actorConfig.getConcurrency())
                    .prefetchCount(actorConfig.getPrefetchCount())
                    .shardCount(actorConfig.getShardCount())
                    .consumer(actorConfig.getConsumer())
                    .producer(actorConfig.getProducer())
                    .delayed(actorConfig.isDelayed())
                    .delayType(actorConfig.getDelayType())
                    .priorityQueue(actorConfig.isPriorityQueue())
                    .queueType(actorConfig.getQueueType())
                    .quorumInitialGroupSize(actorConfig.getQuorumInitialGroupSize())
                    .haMode(actorConfig.getHaMode())
                    .haParams(actorConfig.getHaParams())
                    .maxPriority(actorConfig.getMaxPriority())
                    .maxPriority(actorConfig.getMaxPriority())
                    .lazyMode(actorConfig.isLazyMode())
                    .retryConfig(actorConfig.getRetryConfig())
                    .exceptionHandlerConfig(actorConfig.getExceptionHandlerConfig())
                    .ttlConfig(actorConfig.getTtlConfig())
                    .build();

    @SneakyThrows
    public static <MessageType extends Enum<MessageType>> ActorConfig toActorConfig(final MessageType messageType,
                                                                                    final RoutingKey routingKeyData,
                                                                                    final HierarchicalOperationWorkerConfig workerConfig,
                                                                                    final HierarchicalActorConfig mainActorConfig) {
        val useParentConfigInWorker = mainActorConfig.isUseParentConfigInWorker();
        return ActorConfig.builder()
                // Custom fields
                .exchange(exchangeName(mainActorConfig.getExchange(), messageType, routingKeyData))
                .prefix(prefix(mainActorConfig.getPrefix(), routingKeyData))
                .consumer(consumerConfig(workerConfig.getConsumer(), mainActorConfig.getConsumer(), routingKeyData))
                .producer(producerConfig(workerConfig.getProducer(), mainActorConfig.getProducer(), routingKeyData))

                // Copy parent data
                .concurrency(useParentConfigInWorker ? mainActorConfig.getConcurrency() : workerConfig.getConcurrency())
                .shardCount(useParentConfigInWorker ? mainActorConfig.getShardCount() :
                        Objects.nonNull(workerConfig.getShardCount()) && workerConfig.getShardCount() >= 1 ? workerConfig.getShardCount() : null)
                .priorityQueue(mainActorConfig.isPriorityQueue())
                .maxPriority(useParentConfigInWorker ? mainActorConfig.getMaxPriority() : workerConfig.getMaxPriority())
                .delayed(useParentConfigInWorker ? mainActorConfig.isDelayed() : workerConfig.isDelayed())
                .delayType(useParentConfigInWorker ? mainActorConfig.getDelayType() : workerConfig.getDelayType())
                .queueType(useParentConfigInWorker ? mainActorConfig.getQueueType() : workerConfig.getQueueType())
                .haMode(useParentConfigInWorker ? mainActorConfig.getHaMode() : workerConfig.getHaMode())
                .haParams(useParentConfigInWorker ? mainActorConfig.getHaParams() : workerConfig.getHaParams())
                .lazyMode(useParentConfigInWorker ? mainActorConfig.isLazyMode() : workerConfig.isLazyMode())
                .quorumInitialGroupSize(useParentConfigInWorker ? mainActorConfig.getQuorumInitialGroupSize() : workerConfig.getQuorumInitialGroupSize())
                .retryConfig(useParentConfigInWorker ? mainActorConfig.getRetryConfig() : workerConfig.getRetryConfig())
                .exceptionHandlerConfig(useParentConfigInWorker ? mainActorConfig.getExceptionHandlerConfig() : workerConfig.getExceptionHandlerConfig())
                .ttlConfig(useParentConfigInWorker ? mainActorConfig.getTtlConfig(): workerConfig.getTtlConfig())
                .build();
    }

    public static <MessageType extends Enum<MessageType>> String exchangeName(final String parentExchangeName,
                                                                              final MessageType messageType,
                                                                              final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();

        if (!StringUtils.isEmpty(parentExchangeName)) {
            // For backward compatibility
            if(routingKey.isEmpty()) {
                return parentExchangeName;
            }

            return beautifierFunction.apply(Stream.of(parentExchangeName, String.join(".", routingKey)), ".");
        }

        return beautifierFunction.apply(Stream.of(EXCHANGES, String.join(".", routingKey), messageType.name()), ".");
    }

    private static String prefix(final String parentPrefixName,
                                 final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();
        if (!StringUtils.isEmpty(parentPrefixName)) {
            return beautifierFunction.apply(Stream.of(parentPrefixName, String.join(".", routingKey)), ".");
        }
        return beautifierFunction.apply(Stream.of(ACTORS, String.join(".", routingKey)), ".");
    }


    private static ProducerConfig producerConfig(final ProducerConfig workerConfig,
                                                 final ProducerConfig parentProducerConfig,
                                                 final RoutingKey routingKeyData) {
        if (Objects.isNull(workerConfig)) {
            return MapperUtils.clone(parentProducerConfig, ProducerConfig.class);
        }

        if (Objects.isNull(workerConfig.getConnectionIsolationStrategy())) {
            val isolationStrtegy = MapperUtils.clone(parentProducerConfig.getConnectionIsolationStrategy(), ConnectionIsolationStrategy.class);
            workerConfig.setConnectionIsolationStrategy(isolationStrtegy);
        }
        updateIsolationStrategyConfig(workerConfig.getConnectionIsolationStrategy(), routingKeyData.getRoutingKey());
        return workerConfig;
    }

    private static ConsumerConfig consumerConfig(final ConsumerConfig workerConfig,
                                                 final ConsumerConfig parentConsumerConfig,
                                                 final RoutingKey routingKeyData) {
        if (Objects.isNull(workerConfig)) {
            return MapperUtils.clone(parentConsumerConfig, ConsumerConfig.class);
        }

        if (Objects.isNull(workerConfig.getConnectionIsolationStrategy())) {
            val isolationStrtegy = MapperUtils.clone(parentConsumerConfig.getConnectionIsolationStrategy(), ConnectionIsolationStrategy.class);
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
