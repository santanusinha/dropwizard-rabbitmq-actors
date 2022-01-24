/*
 * Copyright (c) 2019 Santanu Sinha <santanu.sinha@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.appform.dropwizard.actors.common;

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
