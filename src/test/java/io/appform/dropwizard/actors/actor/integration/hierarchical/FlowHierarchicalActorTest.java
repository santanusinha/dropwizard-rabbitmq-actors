package io.appform.dropwizard.actors.actor.integration.hierarchical;

import com.fasterxml.jackson.core.type.TypeReference;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActor;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.OneDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.TwoDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.RoutingKey;
import io.appform.dropwizard.actors.utils.YamlReader;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class FlowHierarchicalActorTest {

    private static final RMQIntegrationTestHelper ROUTER_TEST_HELPER = new RMQIntegrationTestHelper();
    private static final FlowHierarchicalActorConfig<FlowType> RMQ_CONFIG = YamlReader.loadConfig("rmqHierarchical.yaml", new TypeReference<>() {
    });
    private Map<FlowType, HierarchicalActor<FlowType, ActionMessage>> actorActors;

    @SneakyThrows
    public void createActors() {
        actorActors = RMQ_CONFIG.getWorkers()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getKey().accept(new FlowTypeHierarchicalActorBuilder(e.getValue(), ROUTER_TEST_HELPER))));
        Assertions.assertNotNull(actorActors);
        Assertions.assertEquals(actorActors.size(), RMQ_CONFIG.getWorkers().size());

        for (val entry : actorActors.entrySet()) {
            val routerActor = entry.getValue();
            routerActor.start();
        }
    }

    @SneakyThrows
    public void cleanUp() {
        for (val entry : actorActors.entrySet()) {
            entry.getValue().stop();
        }
    }


    @Test
    void testRouter() {
        createActors();
        val messages = Map.of(
                RoutingKey.builder().list(List.of("")).build(),
                OneDataActionMessage.builder()
                        .data("FLOW_ONE")
                        .build(),

                RoutingKey.builder().list(List.of("L1", "L2")).build(),
                OneDataActionMessage.builder()
                        .data("FLOW_ONE-L1-L2-SOME")
                        .build(),

                RoutingKey.builder().list(List.of("L1")).build(),
                TwoDataActionMessage.builder()
                        .data("FLOW_TWO-L1")
                        .build(),

                RoutingKey.builder().list(List.of("FLOW_TWO")).build(),
                TwoDataActionMessage.builder()
                        .data("L2")
                        .build(),

                RoutingKey.builder().list(List.of("L2", "SOME")).build(),
                OneDataActionMessage.builder()
                        .data("FLOW_ONE-L2-L2-SOME")
                        .build()
        );

        messages.forEach((routingKey, message) -> {
            val flowType = message.getType();

            if (actorActors.containsKey(flowType)) {
                val router = actorActors.get(flowType);
                val flowLevelPrefix = Arrays.asList(RMQ_CONFIG.getWorkers().get(flowType).getPrefix().split("\\."));

                Assertions.assertNotNull(router);
                val worker = router.getActorImpl().getWorker().get(flowType, routingKey);
                Assertions.assertNotNull(worker);
                val publisher = worker.getActorImpl().getPublishActor();
                val consumer = worker.getActorImpl().getConsumeActor();

                val publisherQueueName = publisher.getQueueName();
                val consumerQueueName = consumer.getQueueName();
                Assertions.assertNotNull(publisherQueueName);
                Assertions.assertNotNull(consumerQueueName);
                val publisherQueueNameTokens = new LinkedHashSet<>(Arrays.stream(publisherQueueName.split("\\."))
                        .filter(e -> !e.isBlank() && !flowLevelPrefix.contains(e))
                        .toList());
                val consumerQueueNameTokens = new LinkedHashSet<>(Arrays.stream(consumerQueueName.split("\\."))
                        .filter(e -> !e.isBlank() && !flowLevelPrefix.contains(e))
                        .toList());

                val expectedElementsInQueueName = new LinkedHashSet<>(routingKey.getRoutingKey().stream().filter(e -> !e.isBlank()).toList());
                expectedElementsInQueueName.add(flowType.name());

                publisherQueueNameTokens.forEach(ele -> Assertions.assertTrue(expectedElementsInQueueName.contains(ele)));
                consumerQueueNameTokens.forEach(ele -> Assertions.assertTrue(expectedElementsInQueueName.contains(ele)));
                message.setExchangeName(String.join("-", expectedElementsInQueueName));
                try {
                    router.publish(routingKey, message);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        cleanUp();
    }


}
