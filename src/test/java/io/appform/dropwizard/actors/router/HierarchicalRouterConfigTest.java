package io.appform.dropwizard.actors.router;

import com.fasterxml.jackson.core.type.TypeReference;
import io.appform.dropwizard.actors.router.config.HierarchicalRouterConfig;
import io.appform.dropwizard.actors.router.data.ActionMessage;
import io.appform.dropwizard.actors.router.data.C2CDataActionMessage;
import io.appform.dropwizard.actors.router.data.C2MDataActionMessage;
import io.appform.dropwizard.actors.router.data.FlowType;
import io.appform.dropwizard.actors.router.router.FlowTypeHierarchicalRouterBuilder;
import io.appform.dropwizard.actors.router.tree.key.RoutingKey;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

class HierarchicalRouterConfigTest {

    private final static HierarchicalRouterTestHelper ROUTER_TEST_HELPER = new HierarchicalRouterTestHelper();
    private final static HierarchicalRouterConfig<FlowType> RMQ_CONFIG = YamlReader.loadConfig("rmq.yaml", new TypeReference<>() {
    });
    private Map<FlowType, HierarchicalOperationRouter<FlowType, ActionMessage>> actorRouters;

    @SneakyThrows
    public void createActors() {
        actorRouters = RMQ_CONFIG.getWorkers()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getKey().accept(new FlowTypeHierarchicalRouterBuilder(e.getValue(), ROUTER_TEST_HELPER))));
        Assertions.assertNotNull(actorRouters);
        Assertions.assertEquals(actorRouters.size(), RMQ_CONFIG.getWorkers().size());

        for (val entry : actorRouters.entrySet()) {
            val routerActor = entry.getValue();
            routerActor.start();
        }
    }

    @SneakyThrows
    public void cleanUp() {
        for (val entry : actorRouters.entrySet()) {
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

            if (actorRouters.containsKey(flowType)) {
                val router = actorRouters.get(flowType);
                val flowLevelPrefix = Arrays.asList(RMQ_CONFIG.getWorkers().get(flowType).getDefaultData().getPrefix().split("\\."));

                Assertions.assertNotNull(router);
                val worker = router.getWorker().get(flowType, routingKey);
                Assertions.assertNotNull(worker);
                val publisherQueueName = worker.getActorImpl().getPublishActor().queueName();
                Assertions.assertNotNull(publisherQueueName);
                val publisherQueueNameTokens = new LinkedHashSet<>(Arrays.stream(worker
                                .getActorImpl()
                                .getPublishActor()
                                .queueName()
                                .split("\\."))
                        .filter(e -> !e.isBlank() && !flowLevelPrefix.contains(e))
                        .toList());

                val expectedElementsInQueueName = new LinkedHashSet<>(routingKey.getRoutingKey().stream().filter(e -> !e.isBlank()).toList());
                expectedElementsInQueueName.add(flowType.name());

                publisherQueueNameTokens.forEach(ele -> Assertions.assertTrue(expectedElementsInQueueName.contains(ele)));
                router.submit(routingKey, message);
            }
        });

        //cleanUp();
    }


}
