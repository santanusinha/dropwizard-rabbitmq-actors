package io.appform.dropwizard.actors.actor.hierarchical.tree.key;


import lombok.Builder;

import java.util.List;

public class RoutingKey implements HierarchicalRoutingKey<String> {
    private final List<String> list;

    @Builder
    public RoutingKey(final List<String> list) {
        this.list = list;
    }

    @Override
    public List<String> getRoutingKey() {
        return list;
    }
}