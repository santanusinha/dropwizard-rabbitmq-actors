package com.phonepe.platform.rabbitmq.actor.test.connectivity.strategy;

import com.phonepe.platform.rabbitmq.actor.test.actor.ConnectionIsolationLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SharedConnectionStrategy extends ConnectionIsolationStrategy {

    @NotNull
    @NotEmpty
    private String name;

    public SharedConnectionStrategy() {
        super(ConnectionIsolationLevel.SHARED);
    }

    @Builder
    public SharedConnectionStrategy(String name) {
        super(ConnectionIsolationLevel.SHARED);
        this.name = name;
    }

    @Override
    public <T> T accept(ConnectionIsolationStrategyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
