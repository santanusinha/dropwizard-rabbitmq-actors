package io.appform.dropwizard.actors.router.data;

public enum FlowType {
    C2M_AUTH_FLOW {
        @Override
        public <T> T accept(FlowTypeVisitor<T> visitor) {
            return visitor.visitC2M();
        }
    },
    C2C_AUTH_FLOW {
        @Override
        public <T> T accept(FlowTypeVisitor<T> visitor) {
            return visitor.visitC2C();
        }
    };

    public static final String C2M_AUTH_FLOW_TEXT = "C2M_AUTH_FLOW";
    public static final String C2C_AUTH_FLOW_TEXT = "C2C_AUTH_FLOW";

    public abstract <T> T accept(FlowTypeVisitor<T> visitor);
    public interface FlowTypeVisitor<T> {
        T visitC2M();
        T visitC2C();
    }
}
