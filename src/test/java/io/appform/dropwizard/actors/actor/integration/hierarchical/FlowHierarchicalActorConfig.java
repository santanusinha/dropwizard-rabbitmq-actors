package io.appform.dropwizard.actors.actor.integration.hierarchical;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActorConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowHierarchicalActorConfig<MessageType extends Enum<MessageType>> {
    private Map<MessageType, HierarchicalActorConfig> workers;
}
