package io.appform.dropwizard.actors.actor.integration.normal;

import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.C2CDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;


public class C2CDataActionMessageActor extends Actor<FlowType, ActionMessage> {


    public C2CDataActionMessageActor(final FlowType flowType,
                                     final ActorConfig config,
                                     final RMQIntegrationTestHelper routerTestHelper) {
        super(flowType,
                config,
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