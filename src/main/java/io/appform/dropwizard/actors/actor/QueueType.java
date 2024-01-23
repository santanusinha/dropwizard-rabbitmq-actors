package io.appform.dropwizard.actors.actor;

public enum QueueType {
    CLASSIC {
        @Override
        public <T> T handleConfig(QueueTypeVisitor<T> visitor) {
            return visitor.visitClassicQueue();
        }
    },
    CLASSIC_V2 {
        @Override
        public <T> T handleConfig(QueueTypeVisitor<T> visitor) {
            return visitor.visitClassicV2Queue();
        }
    },
    QUORUM {
        @Override
        public <T> T handleConfig(QueueTypeVisitor<T> visitor) {
            return visitor.visitQuorumQueue();
        }
    };

    public static final String X_QUEUE_TYPE = "x-queue-type";

    public abstract <T> T handleConfig(QueueTypeVisitor<T> visitor);
}
