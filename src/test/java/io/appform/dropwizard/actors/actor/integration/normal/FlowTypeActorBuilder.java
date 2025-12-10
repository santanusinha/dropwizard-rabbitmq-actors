package io.appform.dropwizard.actors.actor.integration.normal;

import io.appform.dropwizard.actors.actor.Actor;
import io.appform.dropwizard.actors.actor.ActorConfig;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;

public class FlowTypeActorBuilder implements FlowType.FlowTypeVisitor<Actor<FlowType, ActionMessage>> {

    private final ActorConfig config;
    private final RMQIntegrationTestHelper routerTestHelper;

    public FlowTypeActorBuilder(final ActorConfig config,
                                final RMQIntegrationTestHelper routerTestHelper) {
        this.config = config;
        this.routerTestHelper = routerTestHelper;
    }

    @Override
    public Actor<FlowType, ActionMessage> visitOne() {
        return new OneDataActionMessageActor(FlowType.FLOW_ONE, config, routerTestHelper);
    }

    @Override
    public Actor<FlowType, ActionMessage> visitTwo() {
        return new TwoDataActionMessageActor(FlowType.FLOW_TWO, config, routerTestHelper);
    }
}
