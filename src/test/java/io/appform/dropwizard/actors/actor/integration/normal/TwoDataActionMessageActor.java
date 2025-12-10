package io.appform.dropwizard.actors.actor.integration.normal;

import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.TwoDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TwoDataActionMessageActor extends Actor<FlowType, ActionMessage> {


    public TwoDataActionMessageActor(final FlowType flowType,
                                     final ActorConfig config,
                                     final RMQIntegrationTestHelper routerTestHelper) {
        super(flowType,
                config,
                routerTestHelper.getConnectionRegistry(),
                routerTestHelper.getMapper(),
                routerTestHelper.getRetryStrategyFactory(),
                routerTestHelper.getExceptionHandlingFactory(),
                TwoDataActionMessage.class,
                routerTestHelper.getDroppedExceptionTypes());
    }

    @Override
    protected boolean handle(ActionMessage actionMessage, MessageMetadata messageMetadata) throws Exception {
        log.info("TWO : {}", actionMessage);
        return true;
    }
}