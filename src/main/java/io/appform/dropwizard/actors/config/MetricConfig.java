package io.appform.dropwizard.actors.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricConfig {
    private boolean enabledForAll;
    private Set<String> enabledForQueues;
}
