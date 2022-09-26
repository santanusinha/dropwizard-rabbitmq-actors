package com.phonepe.platform.exceptionhandler.config;

import com.phonepe.platform.exceptionhandler.ExceptionHandlerType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Created by kanika.khetawat on 04/02/20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DropConfig extends ExceptionHandlerConfig {

    public DropConfig() {
        super(ExceptionHandlerType.DROP);
    }

    @Override
    public <T> T accept(ExceptionHandlerConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
