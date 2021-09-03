package io.appform.dropwizard.actors.compression;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompressionConfig {

    @NotNull
    private boolean enableCompression;

    @NotNull
    private CompressionAlgorithm compressionAlgorithm;
}
