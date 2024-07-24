package io.appform.dropwizard.actors.router.tree;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.Maps;
import io.appform.dropwizard.actors.router.tree.key.HierarchicalRoutingKey;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@ToString
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class HierarchicalDataStoreTree<K, V> {

    private final V defaultData;
    @JsonUnwrapped
    private final Map<K, HierarchicalDataStoreTreeNode<String, V>> rootNodes = Maps.newConcurrentMap();

    public HierarchicalDataStoreTree() {
        this.defaultData = null;
    }

    public HierarchicalDataStoreTree(V defaultData) {
        this.defaultData = defaultData;
    }

    public void add(final HierarchicalRoutingKey<String> routingKey, final K key, final V data) {
        rootNodes.computeIfAbsent(key, t -> new HierarchicalDataStoreTreeNode<>(0, String.valueOf(key), defaultData));
        rootNodes.get(key)
                .add(routingKey, data);
    }

    public void traverse(final Consumer<V> consumer) {
        rootNodes.forEach((k, vHierarchicalStoreNode) -> {
            if (vHierarchicalStoreNode != null) {
                vHierarchicalStoreNode.traverse(consumer);
            }
        });
    }

    public V get(final K key, final HierarchicalRoutingKey<String> routingKey) {
        if (!rootNodes.containsKey(key)) {
            log.warn("Key {} not found in {} keys {}. Using default {}", key, rootNodes.keySet(), defaultData);
            return defaultData;
        }
        return rootNodes.get(key)
                .find(routingKey);
    }
}