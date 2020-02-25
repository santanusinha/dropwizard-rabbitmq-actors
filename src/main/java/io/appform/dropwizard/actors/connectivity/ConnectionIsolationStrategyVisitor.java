package io.appform.dropwizard.actors.connectivity;

public interface ConnectionIsolationStrategyVisitor<T> {

    T visit(final ExclusiveConnectionStrategy strategy);

    T visit(final SharedConnectionStrategy strategy);

}
