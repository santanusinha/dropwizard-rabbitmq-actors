package com.phonepe.platform.rabbitmq.actor.test.actor;

public enum ConnectionIsolationLevel {

    DEFAULT {
        @Override
        public <T> T accept(ConnectionIsolationVisitor<T> visitor) {
            return visitor.visitDefault();
        }
    },

    SHARED {
        @Override
        public <T> T accept(ConnectionIsolationVisitor<T> visitor) {
            return visitor.visitShared();
        }
    };

    public abstract <T> T accept(ConnectionIsolationVisitor<T> visitor);

    public interface ConnectionIsolationVisitor<T> {

        T visitShared();

        T visitDefault();
    }

}
