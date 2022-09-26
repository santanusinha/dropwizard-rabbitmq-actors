package com.phonepe.platform.rabbitmq.actor.test.connectivity.strategy;

public interface ConnectionIsolationStrategyVisitor<T> {

    T visit(final SharedConnectionStrategy strategy);

    T visit(final DefaultConnectionStrategy strategy);

}
