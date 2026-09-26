package io.appform.dropwizard.actors.actor;

public interface ExchangeTypeVisitor<T> {

    T visitDirect();

    T visitTopic();

    T visitFanout();
}
