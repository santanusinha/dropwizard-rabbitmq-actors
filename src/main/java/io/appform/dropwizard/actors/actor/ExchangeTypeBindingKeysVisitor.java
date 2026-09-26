package io.appform.dropwizard.actors.actor;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;

/**
 * Resolves the binding key(s) to use while binding a queue to the main exchange, based on the exchange type.
 */
@AllArgsConstructor
public class ExchangeTypeBindingKeysVisitor implements ExchangeTypeVisitor<List<String>> {

    private final ActorConfig actorConfig;
    private final String queue;

    @Override
    public List<String> visitDirect() {
        return Collections.singletonList(queue);
    }

    @Override
    public List<String> visitTopic() {
        final List<String> configured = actorConfig.getBindingKeys();
        if (configured == null || configured.isEmpty()) {
            return Collections.singletonList(queue);
        }
        return configured;
    }

    @Override
    public List<String> visitFanout() {
        return Collections.singletonList("");
    }
}
