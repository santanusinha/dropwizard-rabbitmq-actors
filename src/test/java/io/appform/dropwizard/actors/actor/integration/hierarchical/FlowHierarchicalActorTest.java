package io.appform.dropwizard.actors.actor.integration.hierarchical;

import com.fasterxml.jackson.core.type.TypeReference;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActor;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.C2CDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.C2MDataActionMessage;
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
                C2MDataActionMessage.builder()
                        .data("C2M")
                        .build(),

                RoutingKey.builder().list(List.of("REGULAR", "JAR")).build(),
                C2MDataActionMessage.builder()
                        .data("C2M-REGULAR-JAR-SOME")
                        .build(),

                RoutingKey.builder().list(List.of("REGULAR")).build(),
                C2CDataActionMessage.builder()
                        .data("C2C-REGULAR")
                        .build(),

                RoutingKey.builder().list(List.of("C2C_AUTH_FLOW")).build(),
                C2CDataActionMessage.builder()
                        .data("C2C")
                        .build(),

                RoutingKey.builder().list(List.of("FULL_AUTH", "JAR")).build(),
                C2MDataActionMessage.builder()
                        .data("C2M-FULL_AUTH-JAR-SOME")
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
                val publisherQueueName = worker.getActorImpl().getPublishActor().getQueueName();
                Assertions.assertNotNull(publisherQueueName);
                val publisherQueueNameTokens = new LinkedHashSet<>(Arrays.stream(worker
                                .getActorImpl()
                                .getPublishActor()
                                .getQueueName()
                                .split("\\."))
                        .filter(e -> !e.isBlank() && !flowLevelPrefix.contains(e))
                        .toList());

                val expectedElementsInQueueName = new LinkedHashSet<>(routingKey.getRoutingKey().stream().filter(e -> !e.isBlank()).toList());
                expectedElementsInQueueName.add(flowType.name());

                publisherQueueNameTokens.forEach(ele -> Assertions.assertTrue(expectedElementsInQueueName.contains(ele)));
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
