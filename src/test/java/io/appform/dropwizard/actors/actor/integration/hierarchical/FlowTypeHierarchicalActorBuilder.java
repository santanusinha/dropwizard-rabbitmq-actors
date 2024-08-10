package io.appform.dropwizard.actors.actor.integration.hierarchical;

import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActor;
import io.appform.dropwizard.actors.actor.hierarchical.HierarchicalActorConfig;
import io.appform.dropwizard.actors.actor.integration.RMQIntegrationTestHelper;
import io.appform.dropwizard.actors.actor.integration.data.ActionMessage;
import io.appform.dropwizard.actors.actor.integration.data.FlowType;

public class FlowTypeHierarchicalActorBuilder implements FlowType.FlowTypeVisitor<HierarchicalActor<FlowType, ActionMessage>> {

    private final HierarchicalActorConfig hierarchicalTreeConfig;
    private final RMQIntegrationTestHelper routerTestHelper;

    public FlowTypeHierarchicalActorBuilder(final HierarchicalActorConfig hierarchicalTreeConfig,
                                            final RMQIntegrationTestHelper routerTestHelper) {
        this.hierarchicalTreeConfig = hierarchicalTreeConfig;
        this.routerTestHelper = routerTestHelper;
    }

    @Override
    public HierarchicalActor<FlowType, ActionMessage> visitC2M() {
        return new C2MDataActionMessageHierarchicalActor(FlowType.C2M_AUTH_FLOW, hierarchicalTreeConfig, routerTestHelper);
    }

    @Override
    public HierarchicalActor<FlowType, ActionMessage> visitC2C() {
        return new C2CDataActionMessageHierarchicalActor(FlowType.C2C_AUTH_FLOW, hierarchicalTreeConfig, routerTestHelper);
    }
}
