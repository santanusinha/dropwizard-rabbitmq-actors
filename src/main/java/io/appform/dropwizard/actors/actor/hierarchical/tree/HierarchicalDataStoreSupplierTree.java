package io.appform.dropwizard.actors.actor.hierarchical.tree;

import com.google.common.collect.Lists;
import io.appform.dropwizard.actors.actor.hierarchical.tree.key.RoutingKey;
import lombok.val;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@SuppressWarnings("java:S119")
public class HierarchicalDataStoreSupplierTree<INPUT_NODE_TYPE, INPUT_ROOT_NODE_TYPE, NODE_KEY_TYPE, OUTPUT_NODE_TYPE> extends HierarchicalDataStoreTree<NODE_KEY_TYPE, OUTPUT_NODE_TYPE> {

    private static final Function<List<String>, RoutingKey> routingKeyGenerator = (list) -> RoutingKey.builder()
            .list(list)
            .build();

    public HierarchicalDataStoreSupplierTree(final NODE_KEY_TYPE key,
                                             final HierarchicalTreeConfig<INPUT_ROOT_NODE_TYPE, String, INPUT_NODE_TYPE> treeConfig,
                                             final Function<INPUT_ROOT_NODE_TYPE, INPUT_NODE_TYPE> rootNodeConverterSupplier,
                                             final TriConsumerSupplier<OUTPUT_NODE_TYPE, RoutingKey, NODE_KEY_TYPE, INPUT_NODE_TYPE> supplier) {
        super(supplier.get(
                routingKeyGenerator.apply(List.of()),
                key,
                rootNodeConverterSupplier.apply(treeConfig.getDefaultData())
        ));
        buildTree(key, treeConfig.getChildrenData(), supplier);
    }

    private void buildTree(final NODE_KEY_TYPE key,
                           final HierarchicalDataStoreTreeNode<String, INPUT_NODE_TYPE> childrenList,
                           final TriConsumerSupplier<OUTPUT_NODE_TYPE, RoutingKey, NODE_KEY_TYPE, INPUT_NODE_TYPE> supplier) {
        val tokenList = Lists.<String>newArrayList();
        buildTreeHelper(key, childrenList, tokenList, supplier);
    }

    private void buildTreeHelper(final NODE_KEY_TYPE key,
                                 final HierarchicalDataStoreTreeNode<String, INPUT_NODE_TYPE> rootChildrenData,
                                 final List<String> tokenList,
                                 final TriConsumerSupplier<OUTPUT_NODE_TYPE, RoutingKey, NODE_KEY_TYPE, INPUT_NODE_TYPE> supplier) {
        val childrenList = rootChildrenData.getChildren();
        if (childrenList.isEmpty()) {
            add(routingKeyGenerator.apply(tokenList), key, null);
            return;
        }

        for (String childrenToken : childrenList.keySet()) {
            val currentChildrenData = childrenList.get(childrenToken);

            tokenList.add(childrenToken);

            val routingKey = routingKeyGenerator.apply(tokenList.stream().map(String::valueOf).toList());
            val currentChildrenDefaultData = Objects.nonNull(currentChildrenData.getNodeData()) ?
                    currentChildrenData.getNodeData() : rootChildrenData.getNodeData();

            add(routingKey, key, supplier.get(routingKey, key, currentChildrenDefaultData));
            buildTreeHelper(key, currentChildrenData, tokenList, supplier);

            tokenList.remove(childrenToken);
        }
    }

}
