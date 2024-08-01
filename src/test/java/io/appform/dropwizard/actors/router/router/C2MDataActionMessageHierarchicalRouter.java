package io.appform.dropwizard.actors.router.router;

import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.router.HierarchicalOperationRouter;
import io.appform.dropwizard.actors.router.HierarchicalRouterTestHelper;
import io.appform.dropwizard.actors.router.HierarchicalRouterUtils;
import io.appform.dropwizard.actors.router.config.HierarchicalOperationWorkerConfig;
import io.appform.dropwizard.actors.router.data.ActionMessage;
import io.appform.dropwizard.actors.router.data.C2MDataActionMessage;
import io.appform.dropwizard.actors.router.data.FlowType;
import io.appform.dropwizard.actors.router.tree.HierarchicalTreeConfig;
import org.junit.jupiter.api.Assertions;


public class C2MDataActionMessageHierarchicalRouter extends HierarchicalOperationRouter<FlowType, ActionMessage> {


    public C2MDataActionMessageHierarchicalRouter(final FlowType flowType,
                                                  final HierarchicalTreeConfig<ActorConfig, String, HierarchicalOperationWorkerConfig> actorConfigMap,
                                                  final HierarchicalRouterTestHelper routerTestHelper) {
        super(flowType, actorConfigMap,
                routerTestHelper.getConnectionRegistry(),
                routerTestHelper.getMapper(),
                routerTestHelper.getRetryStrategyFactory(),
                routerTestHelper.getExceptionHandlingFactory(),
                C2MDataActionMessage.class,
                routerTestHelper.getDroppedExceptionTypes());
    }


    @Override
    public boolean process(ActionMessage actionMessage) {
        System.out.println("C2M : " + actionMessage);
        return true;
    }

}