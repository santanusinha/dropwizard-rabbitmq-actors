package io.appform.dropwizard.actors.actor;

public interface HaModeVisitor<T> {

    T visitAll();

    T visitExactly();
}
