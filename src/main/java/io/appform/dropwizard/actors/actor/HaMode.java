package io.appform.dropwizard.actors.actor;

public enum HaMode {
    EXACTLY {
        @Override
        public <T> T handleConfig(HaModeVisitor<T> visitor) {
            return visitor.visitExactly();
        }
    },
    ALL {
        @Override
        public <T> T handleConfig(HaModeVisitor<T> visitor) {
            return visitor.visitAll();
        }
    };

    public abstract <T> T handleConfig(HaModeVisitor<T> visitor);
}
