package io.appform.dropwizard.actors.actor;

public enum ConnectionIsolationLevel {

    SHARED {
        @Override
        public <T> T accept(ConnectionIsolationVisitor<T> visitor) {
            return visitor.visitShared();
        }
    },
    EXCLUSIVE {
        @Override
        public <T> T accept(ConnectionIsolationVisitor<T> visitor) {
            return visitor.visitExclusive();
        }
    };

    public abstract <T> T accept(ConnectionIsolationVisitor<T> visitor);

    public interface ConnectionIsolationVisitor<T> {

        T visitShared();

        T visitExclusive();

    }

}
