package io.appform.dropwizard.actors.actor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExchangeType {
    DIRECT("direct") {
        @Override
        public <T> T handleConfig(ExchangeTypeVisitor<T> visitor) {
            return visitor.visitDirect();
        }
    },
    TOPIC("topic") {
        @Override
        public <T> T handleConfig(ExchangeTypeVisitor<T> visitor) {
            return visitor.visitTopic();
        }
    },
    FANOUT("fanout") {
        @Override
        public <T> T handleConfig(ExchangeTypeVisitor<T> visitor) {
            return visitor.visitFanout();
        }
    };

    private final String amqpType;

    public abstract <T> T handleConfig(ExchangeTypeVisitor<T> visitor);
}
