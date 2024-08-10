package io.appform.dropwizard.actors.actor.hierarchical.tree.key;

import java.util.List;

public interface HierarchicalRoutingKey<R> {
    List<R> getRoutingKey();
}
