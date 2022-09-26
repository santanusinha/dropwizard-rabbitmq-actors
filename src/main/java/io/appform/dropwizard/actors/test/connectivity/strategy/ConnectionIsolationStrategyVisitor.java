package io.appform.dropwizard.actors.test.connectivity.strategy;

public interface ConnectionIsolationStrategyVisitor<T> {

    T visit(final SharedConnectionStrategy strategy);

    T visit(final DefaultConnectionStrategy strategy);

}
