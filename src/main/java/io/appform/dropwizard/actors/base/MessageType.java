package io.appform.dropwizard.actors.base;

public enum MessageType {
    SIMPLE {
        @Override
        public <T, J> T accept(MessageTypeVisitor<T, J> visitor, J data) {
            return visitor.visitSimple(data);
        }
    },
    WRAPPED {
        @Override
        public <T, J> T accept(MessageTypeVisitor<T, J> visitor, J data) {
            return visitor.visitWrapped(data);
        }
    },
    ;

    public static final String SIMPLE_TEXT = "SIMPLE";
    public static final String WRAPPED_TEXT = "WRAPPED";

    public abstract <T, J> T accept(MessageTypeVisitor<T, J> visitor, J data);

    public interface MessageTypeVisitor<T, J> {

        T visitSimple(J data);

        T visitWrapped(J data);
    }
}
