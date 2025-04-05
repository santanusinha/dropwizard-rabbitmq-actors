package io.appform.dropwizard.actors.actor.integration.hierarchical;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActor;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActorConfig;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.C2MDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;


public class C2MDataActionMessageHierarchicalActor extends HierarchicalActor<FlowType, ActionMessage> {


    public C2MDataActionMessageHierarchicalActor(final FlowType flowType,
                                                 final HierarchicalActorConfig hierarchicalTreeConfig,
                                                 final RMQIntegrationTestHelper routerTestHelper) {
        super(flowType,
                hierarchicalTreeConfig,
                routerTestHelper.getConnectionRegistry(),
                routerTestHelper.getMapper(),
                routerTestHelper.getRetryStrategyFactory(),
                routerTestHelper.getExceptionHandlingFactory(),
                C2MDataActionMessage.class,
                routerTestHelper.getDroppedExceptionTypes());
    }

    @Override
    protected boolean handle(ActionMessage actionMessage, MessageMetadata messageMetadata) throws Exception {
        System.out.println("C2M : " + actionMessage);
        return true;
    }
}