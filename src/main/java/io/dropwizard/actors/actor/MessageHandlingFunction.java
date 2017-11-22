package io.dropwizard.actors.actor;

/**
 *
 */
@FunctionalInterface
public interface MessageHandlingFunction<T,R> {
    R apply(T param) throws Exception;
}
