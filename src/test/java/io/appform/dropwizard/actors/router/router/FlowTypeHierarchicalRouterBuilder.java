package io.appform.dropwizard.actors.router.router;

import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.router.HierarchicalOperationRouter;
import io.appform.dropwizard.actors.router.HierarchicalRouterTestHelper;
import io.appform.dropwizard.actors.router.config.HierarchicalOperationWorkerConfig;
import io.appform.dropwizard.actors.router.data.ActionMessage;
import io.appform.dropwizard.actors.router.data.FlowType;
import io.appform.dropwizard.actors.router.tree.HierarchicalTreeConfig;

public class FlowTypeHierarchicalRouterBuilder implements FlowType.FlowTypeVisitor<HierarchicalOperationRouter<FlowType, ActionMessage>> {

    private final HierarchicalTreeConfig<ActorConfig, String, HierarchicalOperationWorkerConfig> hierarchicalTreeConfig;
    private final HierarchicalRouterTestHelper routerTestHelper;

    public FlowTypeHierarchicalRouterBuilder(final HierarchicalTreeConfig<ActorConfig, String, HierarchicalOperationWorkerConfig> hierarchicalTreeConfig,
                                             final HierarchicalRouterTestHelper routerTestHelper) {
        this.hierarchicalTreeConfig = hierarchicalTreeConfig;
        this.routerTestHelper = routerTestHelper;
    }

    @Override
    public HierarchicalOperationRouter<FlowType, ActionMessage> visitC2M() {
        return new C2MDataActionMessageHierarchicalRouter(FlowType.C2M_AUTH_FLOW, hierarchicalTreeConfig, routerTestHelper);
    }

    @Override
    public HierarchicalOperationRouter<FlowType, ActionMessage> visitC2C() {
        return new C2CDataActionMessageHierarchicalRouter(FlowType.C2C_AUTH_FLOW, hierarchicalTreeConfig, routerTestHelper);
    }
}
