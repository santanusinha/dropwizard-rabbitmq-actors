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

    /**
     * <p>This param will reused all Parent Level ActorConfig while creating all child actors,
     * if marked as false, every children will need tp provide Actor config specific to child</p>
     *
     */
    private boolean useParentConfigInWorker = true;

    @JsonUnwrapped
    private HierarchicalDataStoreTreeNode<String, HierarchicalSubActorConfig> children;

}