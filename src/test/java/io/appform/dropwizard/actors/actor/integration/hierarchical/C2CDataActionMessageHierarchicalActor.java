package io.appform.dropwizard.actors.actor.integration.hierarchical;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActor;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActorConfig;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.C2CDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;


public class C2CDataActionMessageHierarchicalActor extends HierarchicalActor<FlowType, ActionMessage> {


    public C2CDataActionMessageHierarchicalActor(final FlowType flowType,
                                                 final HierarchicalActorConfig hierarchicalTreeConfig,
                                                 final RMQIntegrationTestHelper routerTestHelper) {
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
    protected boolean handle(ActionMessage actionMessage, MessageMetadata messageMetadata) throws Exception {
        System.out.println("C2C : " + actionMessage);
        return true;
    }
}