package io.appform.dropwizard.actors.router.tree;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HierarchicalTreeConfig<K, V> {
    private V defaultData;
    @JsonUnwrapped
    private HierarchicalDataStoreTreeNode<K, V> childrenData;
}
