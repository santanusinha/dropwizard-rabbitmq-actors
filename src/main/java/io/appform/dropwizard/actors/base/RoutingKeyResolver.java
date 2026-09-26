package io.appform.dropwizard.actors.base;

/**
 * Resolves the routing key used while publishing a message. This is primarily meant for TOPIC exchanges
 * where the routing key needs to be derived from the message contents to leverage pattern based routing.
 */
public interface RoutingKeyResolver<M> {
    String resolve(final M message);
}
