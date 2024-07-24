package io.appform.dropwizard.actors.router.tree.key;

import java.util.List;

public interface HierarchicalRoutingKey<R> {
    List<R> getRoutingKey();
}
