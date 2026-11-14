package io.appform.dropwizard.actors.messaging;

/**
 * Derives the routing key for a message published to a DIRECT or TOPIC exchange. Not used for FANOUT.
 */
@FunctionalInterface
public interface RoutingKeyResolver<M> {
    String resolve(M message);
}
