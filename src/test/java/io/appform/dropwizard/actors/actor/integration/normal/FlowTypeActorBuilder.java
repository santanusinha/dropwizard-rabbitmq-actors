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
    public Actor<FlowType, ActionMessage> visitC2M() {
        return new C2MDataActionMessageActor(FlowType.C2M_AUTH_FLOW, config, routerTestHelper);
    }

    @Override
    public Actor<FlowType, ActionMessage> visitC2C() {
        return new C2CDataActionMessageActor(FlowType.C2C_AUTH_FLOW, config, routerTestHelper);
    }
}
