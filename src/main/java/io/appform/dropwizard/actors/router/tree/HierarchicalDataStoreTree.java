package io.appform.dropwizard.actors.router.tree;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.Maps;
import io.appform.dropwizard.actors.router.tree.key.HierarchicalRoutingKey;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@ToString
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@SuppressWarnings("java:S119")
public class HierarchicalDataStoreTree<NODE_KEY_TYPE, NODE_TYPE> {

    private final NODE_TYPE defaultData;
    @JsonUnwrapped
    private final Map<NODE_KEY_TYPE, HierarchicalDataStoreTreeNode<String, NODE_TYPE>> rootNodes = Maps.newConcurrentMap();

    public HierarchicalDataStoreTree() {
        this.defaultData = null;
    }

    public HierarchicalDataStoreTree(NODE_TYPE defaultData) {
        this.defaultData = defaultData;
    }

    public void add(final HierarchicalRoutingKey<String> routingKey, final NODE_KEY_TYPE key, final NODE_TYPE data) {
        rootNodes.computeIfAbsent(key, t -> new HierarchicalDataStoreTreeNode<>(0, String.valueOf(key), defaultData));
        if (Objects.isNull(data)) {
            return;
        }
        rootNodes.get(key)
                .add(routingKey, data);
    }

    public void traverse(final Consumer<NODE_TYPE> consumer) {
        rootNodes.forEach((NODEKEYTYPE, vHierarchicalStoreNode) -> {
            if (vHierarchicalStoreNode != null) {
                vHierarchicalStoreNode.traverse(consumer);
            }
        });
    }

    public NODE_TYPE get(final NODE_KEY_TYPE key, final HierarchicalRoutingKey<String> routingKey) {
        if (!rootNodes.containsKey(key)) {
            log.warn("Key {} not found in {} keys {}. Using default {}", key, rootNodes.keySet(), defaultData);
            return defaultData;
        }

        val routingKeyToken = routingKey.getRoutingKey();
        if (routingKeyToken== null || routingKeyToken.isEmpty()) {
            log.warn("keys are empty {}. Using default {}", key, rootNodes.keySet(), defaultData);
            return defaultData;
        }

        return rootNodes.get(key)
                .find(routingKey);
    }
}