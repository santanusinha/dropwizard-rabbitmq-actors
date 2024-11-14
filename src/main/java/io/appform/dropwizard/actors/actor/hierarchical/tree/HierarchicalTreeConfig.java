package io.appform.dropwizard.actors.actor.hierarchical.tree;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("java:S119")
public class HierarchicalTreeConfig<ROOT_TYPE, NODE_KEY_TYPE, NODE_TYPE> {
    private ROOT_TYPE defaultData;
    @JsonUnwrapped
    private HierarchicalDataStoreTreeNode<NODE_KEY_TYPE, NODE_TYPE> childrenData;
}
