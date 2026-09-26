package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.ExchangeType;
import io.appform.dropwizard.actors.actor.hierarchical.tree.HierarchicalDataStoreTreeNode;
import io.dropwizard.validation.ValidationMethod;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class HierarchicalActorConfig extends ActorConfig {

    /**
     * <p>This param will reuse all Parent Level ActorConfig while creating all child actors,
     * if marked as false, every child will need to provide Actor config specific to child</p>
     */
    private boolean useParentConfigInWorker = true;

    @JsonUnwrapped
    private HierarchicalDataStoreTreeNode<String, HierarchicalSubActorConfig> children;

    /**
     * Hierarchical actors do not yet support TOPIC/FANOUT exchanges: HierarchicalRouterHelper builds each
     * child's ActorConfig without a RoutingKeyResolver and without per-child binding keys, so a non-DIRECT
     * child would silently stay DIRECT or fail at start. Fail fast instead.
     */
    @ValidationMethod(message = "Hierarchical actors currently support only DIRECT exchanges. "
            + "exchangeType (TOPIC/FANOUT) and bindingKeys are not yet supported for hierarchical actors.")
    public boolean isValidExchangeTypeForHierarchical() {
        return getExchangeType() == ExchangeType.DIRECT
                && (getBindingKeys() == null || getBindingKeys().isEmpty());
    }

}