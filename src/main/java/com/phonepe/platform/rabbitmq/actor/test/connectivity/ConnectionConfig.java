package com.phonepe.platform.rabbitmq.actor.test.connectivity;

import com.phonepe.platform.rabbitmq.actor.test.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionConfig {

    @NotNull
    @NotEmpty
    private String name;

    @Min(1)
    @Max(Constants.MAX_THREADS_PER_CONNECTION)
    private int threadPoolSize;

}
