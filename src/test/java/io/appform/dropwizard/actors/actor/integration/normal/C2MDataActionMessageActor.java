package io.appform.dropwizard.actors.actor.integration.normal;

import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.C2MDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;


public class C2MDataActionMessageActor extends Actor<FlowType, ActionMessage> {


    public C2MDataActionMessageActor(final FlowType flowType,
                                     final ActorConfig config,
                                     final RMQIntegrationTestHelper routerTestHelper) {
        super(flowType,
                config,
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