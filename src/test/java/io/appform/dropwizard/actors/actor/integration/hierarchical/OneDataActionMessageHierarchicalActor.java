package io.appform.dropwizard.actors.actor.integration.hierarchical;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActor;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActorConfig;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;
import io.appform.dropwizard.actors.actor.integration.data.OneDataActionMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OneDataActionMessageHierarchicalActor extends HierarchicalActor<FlowType, ActionMessage> {


    public OneDataActionMessageHierarchicalActor(final FlowType flowType,
                                                 final HierarchicalActorConfig hierarchicalTreeConfig,
                                                 final RMQIntegrationTestHelper routerTestHelper) {
        super(flowType,
                hierarchicalTreeConfig,
                routerTestHelper.getConnectionRegistry(),
                routerTestHelper.getMapper(),
                routerTestHelper.getRetryStrategyFactory(),
                routerTestHelper.getExceptionHandlingFactory(),
                OneDataActionMessage.class,
                routerTestHelper.getDroppedExceptionTypes());
    }

    @Override
    protected boolean handle(ActionMessage actionMessage, MessageMetadata messageMetadata) throws Exception {
        log.info("ONE : {}", actionMessage);
        return true;
    }
}