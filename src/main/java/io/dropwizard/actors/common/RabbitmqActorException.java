package io.dropwizard.actors.common;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Exception object
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RabbitmqActorException extends RuntimeException {

    private final ErrorCode errorCode;

    @Builder
    public RabbitmqActorException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public static RabbitmqActorException propagate(final Throwable throwable) {
        return propagate("Error orrcurred", throwable);
    }

    public static RabbitmqActorException propagate(final String message, final Throwable throwable) {
        return new RabbitmqActorException(ErrorCode.INTERNAL_ERROR, message, throwable);
    }
}
