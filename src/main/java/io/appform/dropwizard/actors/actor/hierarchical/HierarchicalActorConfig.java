package io.appform.dropwizard.actors.actor.hierarchical;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.hierarchical.tree.HierarchicalDataStoreTreeNode;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class HierarchicalActorConfig extends ActorConfig {

    @JsonUnwrapped
    private HierarchicalDataStoreTreeNode<String, HierarchicalOperationWorkerConfig> children;

}