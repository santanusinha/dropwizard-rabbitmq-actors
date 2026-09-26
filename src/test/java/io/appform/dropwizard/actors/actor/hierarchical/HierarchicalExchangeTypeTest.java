package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.actor.ExchangeType;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.RoutingKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Hierarchical actors currently support only DIRECT exchanges. A non-DIRECT exchangeType (or bindingKeys)
 * must fail fast - both via config validation and via a runtime guard in HierarchicalRouterHelper - rather
 * than silently staying DIRECT.
 */
class HierarchicalExchangeTypeTest {

    enum Type { TEST }

    private HierarchicalActorConfig config(ExchangeType exchangeType, List<String> bindingKeys) {
        HierarchicalActorConfig config = new HierarchicalActorConfig();
        config.setExchange("ex");
        config.setPrefix("p");
        config.setExchangeType(exchangeType);
        config.setBindingKeys(bindingKeys);
        return config;
    }

    @Test
    void testValidationRejectsTopic() {
        HierarchicalActorConfig config = config(ExchangeType.TOPIC, List.of("order.*"));
        Assertions.assertFalse(config.isValidExchangeTypeForHierarchical());
    }

    @Test
    void testValidationRejectsFanout() {
        HierarchicalActorConfig config = config(ExchangeType.FANOUT, null);
        Assertions.assertFalse(config.isValidExchangeTypeForHierarchical());
    }

    @Test
    void testValidationRejectsDirectWithBindingKeys() {
        // bindingKeys without TOPIC is meaningless for hierarchical (children are built without them).
        HierarchicalActorConfig config = config(ExchangeType.DIRECT, List.of("order.*"));
        Assertions.assertFalse(config.isValidExchangeTypeForHierarchical());
    }

    @Test
    void testValidationAllowsDefaultDirect() {
        HierarchicalActorConfig config = config(ExchangeType.DIRECT, null);
        Assertions.assertTrue(config.isValidExchangeTypeForHierarchical());
    }

    @Test
    void testToActorConfigThrowsOnTopic() {
        HierarchicalRouterHelper helper = new HierarchicalRouterHelper(new ObjectMapper());
        HierarchicalActorConfig parent = config(ExchangeType.TOPIC, List.of("order.*"));
        RoutingKey routingKey = RoutingKey.builder().list(List.of("child")).build();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> helper.toActorConfig(Type.TEST, routingKey, new HierarchicalSubActorConfig(), parent));
    }

    @Test
    void testToActorConfigThrowsOnFanout() {
        HierarchicalRouterHelper helper = new HierarchicalRouterHelper(new ObjectMapper());
        HierarchicalActorConfig parent = config(ExchangeType.FANOUT, null);
        RoutingKey routingKey = RoutingKey.builder().list(List.of("child")).build();
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> helper.toActorConfig(Type.TEST, routingKey, new HierarchicalSubActorConfig(), parent));
    }

    @Test
    void testToActorConfigAllowsDirect() {
        HierarchicalRouterHelper helper = new HierarchicalRouterHelper(new ObjectMapper());
        HierarchicalActorConfig parent = config(ExchangeType.DIRECT, null);
        RoutingKey routingKey = RoutingKey.builder().list(List.of("child")).build();
        Assertions.assertDoesNotThrow(
                () -> helper.toActorConfig(Type.TEST, routingKey, new HierarchicalSubActorConfig(), parent));
    }
}
