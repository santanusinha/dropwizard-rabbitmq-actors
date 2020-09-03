package io.appform.dropwizard.actors.exceptionhandler.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.appform.dropwizard.actors.exceptionhandler.ExceptionHandlerType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Created by kanika.khetawat on 04/02/20
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
        defaultImpl = SidelineConfig.class)
@JsonSubTypes({
        @JsonSubTypes.Type(name = "SIDELINE", value = SidelineConfig.class),
        @JsonSubTypes.Type(name = "DROP", value = DropConfig.class)
})
@Data
@EqualsAndHashCode
@ToString
public abstract class ExceptionHandlerConfig {

    private final ExceptionHandlerType type;

    public ExceptionHandlerConfig(ExceptionHandlerType type) {
        this.type = type;
    }

    abstract public <T> T accept(ExceptionHandlerConfigVisitor<T> visitor);
}
