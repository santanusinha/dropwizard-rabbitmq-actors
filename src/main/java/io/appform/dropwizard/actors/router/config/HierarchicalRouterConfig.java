package io.appform.dropwizard.actors.router.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.router.tree.HierarchicalTreeConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HierarchicalRouterConfig<MessageType extends Enum<MessageType>> {
    private Map<MessageType, HierarchicalTreeConfig<String, ActorConfig>> workers;
}
