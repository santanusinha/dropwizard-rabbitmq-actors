package io.appform.dropwizard.actors.router.tree;

import com.google.common.collect.Lists;
import io.appform.dropwizard.actors.router.tree.key.RoutingKey;
import lombok.val;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class HierarchicalDataStoreSupplierTree<INPUT, KEY, OUTPUT> extends HierarchicalDataStoreTree<KEY, OUTPUT> {

    private static final Function<List<String>, RoutingKey> routingKeyGenerator = (list) -> RoutingKey.builder()
            .list(list)
            .build();

    public HierarchicalDataStoreSupplierTree(final KEY key,
                                             final HierarchicalTreeConfig<String, INPUT> treeConfig,
                                             final TriConsumerSupplier<OUTPUT, RoutingKey, KEY, INPUT> supplier) {
        super(supplier.get(routingKeyGenerator.apply(List.of()), key, treeConfig.getDefaultData()));
        buildTree(key, treeConfig, supplier);
    }

    private void buildTree(final KEY key,
                           final HierarchicalTreeConfig<String, INPUT> treeConfig,
                           final TriConsumerSupplier<OUTPUT, RoutingKey, KEY, INPUT> supplier) {
        val childrenList = treeConfig.getChildrenData();
        val tokenList = Lists.<String>newArrayList();
        buildTreeHelper(key, childrenList, tokenList, supplier);
    }

    private void buildTreeHelper(final KEY key,
                                 final HierarchicalDataStoreTreeNode<String, INPUT> rootChildrenData,
                                 final List<String> tokenList,
                                 final TriConsumerSupplier<OUTPUT, RoutingKey, KEY, INPUT> supplier) {
        val childrenList = rootChildrenData.getChildren();
        if (childrenList.isEmpty()) {
            return;
        }

        for (String childrenToken : childrenList.keySet()) {
            val currentChildrenData = childrenList.get(childrenToken);

            tokenList.add(childrenToken);

            val routingKey = routingKeyGenerator.apply(tokenList.stream().map(String::valueOf).toList());
            val currentChildrenDefaultData = Objects.nonNull(currentChildrenData.getDefaultData()) ?
                    currentChildrenData.getDefaultData() : rootChildrenData.getDefaultData();

            add(routingKey, key, supplier.get(routingKey, key, currentChildrenDefaultData));
            buildTreeHelper(key, currentChildrenData, tokenList, supplier);

            tokenList.remove(childrenToken);
        }
    }


}
