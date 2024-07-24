package io.appform.dropwizard.actors.router;


import io.appform.dropwizard.actors.router.tree.key.HierarchicalRoutingKey;

public interface HierarchicalMessageRouter<Message> {

    void start();

    void stop();

    void submit(final HierarchicalRoutingKey<String> routingKey, final Message message);

}
