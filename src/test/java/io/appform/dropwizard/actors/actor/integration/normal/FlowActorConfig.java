package io.appform.dropwizard.actors.actor.integration.normal;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActorConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowActorConfig<MessageType extends Enum<MessageType>> {
    private Map<MessageType, ActorConfig> workers;
}
