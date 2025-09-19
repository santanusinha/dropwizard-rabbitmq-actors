package io.appform.dropwizard.actors.connectivity;

import io.appform.dropwizard.actors.common.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
