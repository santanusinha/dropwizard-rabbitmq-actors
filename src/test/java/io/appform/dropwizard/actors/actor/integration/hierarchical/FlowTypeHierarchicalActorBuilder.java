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
    public HierarchicalActor<FlowType, ActionMessage> visitOne() {
        return new OneDataActionMessageHierarchicalActor(FlowType.FLOW_ONE, hierarchicalTreeConfig, routerTestHelper);
    }

    @Override
    public HierarchicalActor<FlowType, ActionMessage> visitTwo() {
        return new TwoDataActionMessageHierarchicalActor(FlowType.FLOW_TWO, hierarchicalTreeConfig, routerTestHelper);
    }
}
