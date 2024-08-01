package io.appform.dropwizard.actors.router.router;

import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.router.HierarchicalOperationRouter;
import io.appform.dropwizard.actors.router.HierarchicalRouterTestHelper;
import io.appform.dropwizard.actors.router.config.HierarchicalOperationWorkerConfig;
import io.appform.dropwizard.actors.router.data.ActionMessage;
import io.appform.dropwizard.actors.router.data.C2CDataActionMessage;
import io.appform.dropwizard.actors.router.data.FlowType;
import io.appform.dropwizard.actors.router.tree.HierarchicalTreeConfig;


public class C2CDataActionMessageHierarchicalRouter extends HierarchicalOperationRouter<FlowType, ActionMessage> {


    public C2CDataActionMessageHierarchicalRouter(final FlowType flowType,
                                                  final HierarchicalTreeConfig<ActorConfig, String, HierarchicalOperationWorkerConfig> hierarchicalTreeConfig,
                                                  final HierarchicalRouterTestHelper routerTestHelper) {
        super(flowType,
                hierarchicalTreeConfig,
                routerTestHelper.getConnectionRegistry(),
                routerTestHelper.getMapper(),
                routerTestHelper.getRetryStrategyFactory(),
                routerTestHelper.getExceptionHandlingFactory(),
                C2CDataActionMessage.class,
                routerTestHelper.getDroppedExceptionTypes());
    }

    @Override
    public boolean process(ActionMessage actionMessage) {
        System.out.println("C2C : " + actionMessage);
        return true;
    }
}