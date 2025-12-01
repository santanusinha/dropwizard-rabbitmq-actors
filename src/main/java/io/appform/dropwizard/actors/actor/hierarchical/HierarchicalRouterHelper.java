package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.ConsumerConfig;
import io.appform.dropwizard.actors.actor.ProducerConfig;
import io.appform.dropwizard.actors.connectivity.strategy.ConnectionIsolationStrategy;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.RoutingKey;
import io.appform.dropwizard.actors.utils.SerDeProvider;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HierarchicalRouterHelper {

    private static final String EXCHANGES = "exchanges";
    private static final String ACTORS = "actors";

    private final SerDeProvider serDeProvider;

    public HierarchicalRouterHelper(ObjectMapper mapper) {
        this.serDeProvider = new SerDeProvider(mapper);
    }

    private static final BiFunction<Stream<String>, String, String> beautifierFunction = (stream, delimiter) -> stream
            .filter(e -> !StringUtils.isEmpty(e))
            .collect(Collectors.joining(delimiter));

    public static final Function<HierarchicalActorConfig, HierarchicalSubActorConfig> actorConfigToSubActorConfigFunc =
            actorConfig -> HierarchicalSubActorConfig.builder()
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
                    .lazyMode(actorConfig.isLazyMode())
                    .retryConfig(actorConfig.getRetryConfig())
                    .exceptionHandlerConfig(actorConfig.getExceptionHandlerConfig())
                    .ttlConfig(actorConfig.getTtlConfig())
                    .build();

    @SneakyThrows
    public <MessageType extends Enum<MessageType>> ActorConfig toActorConfig(final MessageType messageType,
                                                                             final RoutingKey routingKeyData,
                                                                             final HierarchicalSubActorConfig subActorConfig,
                                                                             final HierarchicalActorConfig mainActorConfig) {
        val useParentConfigInWorker = mainActorConfig.isUseParentConfigInWorker();
        return ActorConfig.builder()
                // Custom fields
                .exchange(exchangeName(mainActorConfig.getExchange(), messageType, routingKeyData))
                .prefix(prefix(mainActorConfig.getPrefix(), routingKeyData))
                .consumer(consumerConfig(subActorConfig.getConsumer(), mainActorConfig.getConsumer()))
                .producer(producerConfig(subActorConfig.getProducer(), mainActorConfig.getProducer()))

                // Direct subactor config
                .concurrency(subActorConfig.getConcurrency())
                .prefetchCount(subActorConfig.getPrefetchCount())
                .shardCount(Objects.nonNull(subActorConfig.getShardCount()) && subActorConfig.getShardCount() >= 1 ? subActorConfig.getShardCount() : null)

                // Copy from parent if useParentConfigInWorker is set
                .queueType(useParentConfigInWorker ? mainActorConfig.getQueueType() : subActorConfig.getQueueType())
                .retryConfig(useParentConfigInWorker ? mainActorConfig.getRetryConfig() : subActorConfig.getRetryConfig())
                .exceptionHandlerConfig(useParentConfigInWorker ? mainActorConfig.getExceptionHandlerConfig() : subActorConfig.getExceptionHandlerConfig())

                // Direct from Parent
                .haMode(mainActorConfig.getHaMode())
                .haParams(mainActorConfig.getHaParams())
                .lazyMode(mainActorConfig.isLazyMode())
                .priorityQueue(mainActorConfig.isPriorityQueue())
                .maxPriority(mainActorConfig.getMaxPriority())
                .quorumInitialGroupSize(mainActorConfig.getQuorumInitialGroupSize())
                .delayed(mainActorConfig.isDelayed())
                .delayType(mainActorConfig.getDelayType())
                .ttlConfig(mainActorConfig.getTtlConfig())
                .build();
    }

    public <MessageType extends Enum<MessageType>> String exchangeName(final String parentExchangeName,
                                                                       final MessageType messageType,
                                                                       final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();

        if (!StringUtils.isEmpty(parentExchangeName)) {
            // For backward compatibility
            if (routingKey.isEmpty()) {
                return parentExchangeName;
            }

            return beautifierFunction.apply(Stream.of(parentExchangeName, String.join(".", routingKey)), ".");
        }

        return beautifierFunction.apply(Stream.of(EXCHANGES, String.join(".", routingKey), messageType.name()), ".");
    }

    private String prefix(final String parentPrefixName,
                          final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();
        if (!StringUtils.isEmpty(parentPrefixName)) {
            return beautifierFunction.apply(Stream.of(parentPrefixName, String.join(".", routingKey)), ".");
        }
        return beautifierFunction.apply(Stream.of(ACTORS, String.join(".", routingKey)), ".");
    }


    private ProducerConfig producerConfig(final ProducerConfig subActorConfig,
                                          final ProducerConfig parentProducerConfig) {
        if (Objects.isNull(subActorConfig)) {
            return serDeProvider.clone(parentProducerConfig, ProducerConfig.class);
        }

        if (Objects.isNull(subActorConfig.getConnectionIsolationStrategy())) {
            val isolationStrategy = serDeProvider.clone(parentProducerConfig.getConnectionIsolationStrategy(), ConnectionIsolationStrategy.class);
            subActorConfig.setConnectionIsolationStrategy(isolationStrategy);
        }
        return subActorConfig;
    }

    private ConsumerConfig consumerConfig(final ConsumerConfig subActorConfig,
                                          final ConsumerConfig parentConsumerConfig) {
        if (Objects.isNull(subActorConfig)) {
            return serDeProvider.clone(parentConsumerConfig, ConsumerConfig.class);
        }

        if (Objects.isNull(subActorConfig.getConnectionIsolationStrategy())) {
            val isolationStrategy = serDeProvider.clone(parentConsumerConfig.getConnectionIsolationStrategy(), ConnectionIsolationStrategy.class);
            subActorConfig.setConnectionIsolationStrategy(isolationStrategy);
        }
        return subActorConfig;
    }

}
