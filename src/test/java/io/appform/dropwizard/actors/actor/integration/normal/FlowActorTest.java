package io.appform.dropwizard.actors.actor.integration.normal;

import com.fasterxml.jackson.core.type.TypeReference;
import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.C2CDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.C2MDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;
import io.appform.dropwizard.actors.utils.YamlReader;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class FlowActorTest {

    private final static RMQIntegrationTestHelper ROUTER_TEST_HELPER = new RMQIntegrationTestHelper();
    private final static FlowActorConfig<FlowType> RMQ_CONFIG = YamlReader.loadConfig("rmq.yaml", new TypeReference<>() {
    });
    private Map<FlowType, Actor<FlowType, ActionMessage>> actorActors;

    @SneakyThrows
    public void createActors() {
        actorActors = RMQ_CONFIG.getWorkers()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getKey().accept(new FlowTypeActorBuilder(e.getValue(), ROUTER_TEST_HELPER))));
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
        val messages = List.of(
                C2MDataActionMessage.builder()
                        .data("C2M")
                        .build(),

                C2MDataActionMessage.builder()
                        .data("C2M-REGULAR-JAR-SOME")
                        .build(),

                C2CDataActionMessage.builder()
                        .data("C2C-REGULAR")
                        .build(),

                C2CDataActionMessage.builder()
                        .data("C2C")
                        .build(),

                C2MDataActionMessage.builder()
                        .data("C2M-FULL_AUTH-JAR-SOME")
                        .build()
        );

        messages.forEach(message -> {
            val flowType = message.getType();

            if (actorActors.containsKey(flowType)) {
                val router = actorActors.get(flowType);
                val flowLevelPrefix = Arrays.asList(RMQ_CONFIG.getWorkers().get(flowType).getPrefix().split("\\."));

                Assertions.assertNotNull(router);
                val publisherQueueName = router.getActorImpl().getPublishActor().queueName();
                Assertions.assertNotNull(publisherQueueName);
                message.setExchangeName(String.join("-", publisherQueueName));
                try {
                    router.publish(message);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        cleanUp();
    }


}
