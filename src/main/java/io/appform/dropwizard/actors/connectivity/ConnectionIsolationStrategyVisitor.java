package io.appform.dropwizard.actors.connectivity;

public interface ConnectionIsolationStrategyVisitor<T> {

    T visit(ExclusiveConnectionStrategy strategy);

    T visit(SharedConnectionStrategy strategy);

}
