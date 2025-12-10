package io.appform.dropwizard.actors.actor.integration.hierarchical;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActor;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActorConfig;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.TwoDataActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class TwoDataActionMessageHierarchicalActor extends HierarchicalActor<FlowType, ActionMessage> {


    public TwoDataActionMessageHierarchicalActor(final FlowType flowType,
                                                 final HierarchicalActorConfig hierarchicalTreeConfig,
                                                 final RMQIntegrationTestHelper routerTestHelper) {
        super(flowType,
                hierarchicalTreeConfig,
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