package com.phonepe.platform.connectivity.strategy;

import com.phonepe.platform.actor.ConnectionIsolationLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DefaultConnectionStrategy extends ConnectionIsolationStrategy {


    public DefaultConnectionStrategy() {
        super(ConnectionIsolationLevel.DEFAULT);
    }

    @Override
    public <T> T accept(ConnectionIsolationStrategyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
