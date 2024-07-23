package io.appform.dropwizard.actors.failurehandler.config;

import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlerType;
import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.dropwizard.actors.exceptionhandler.config.ExceptionHandlerConfigVisitor;
import io.appform.dropwizard.actors.failurehandler.handlers.FailureHandlerType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DropConfig extends FailureHandlerConfig {

    public DropConfig() {
        super(FailureHandlerType.DROP);
    }

    @Override
    public <T> T accept(FailureHandlerConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
