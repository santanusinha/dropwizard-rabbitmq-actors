package io.appform.dropwizard.actors.connectivity.strategy;

public interface ConnectionIsolationStrategyVisitor<T> {

    T visit(final SharedConnectionStrategy strategy);

    T visit(final DefaultConnectionStrategy strategy);

}
