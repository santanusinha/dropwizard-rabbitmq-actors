package io.appform.dropwizard.actors.connectivity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
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
    @Max(100)
    private int threadPoolSize;

}
