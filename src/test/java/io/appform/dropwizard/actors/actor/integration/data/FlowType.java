package io.appform.dropwizard.actors.actor.integration.data;

public enum FlowType {
    FLOW_ONE {
        @Override
        public <T> T accept(FlowTypeVisitor<T> visitor) {
            return visitor.visitOne();
        }
    },
    FLOW_TWO {
        @Override
        public <T> T accept(FlowTypeVisitor<T> visitor) {
            return visitor.visitTwo();
        }
    };

    public static final String FLOW_ONE_TEXT = "FLOW_ONE";
    public static final String FLOW_TWO_TEXT = "FLOW_TWO";

    public abstract <T> T accept(FlowTypeVisitor<T> visitor);
    public interface FlowTypeVisitor<T> {
        T visitOne();
        T visitTwo();
    }
}
