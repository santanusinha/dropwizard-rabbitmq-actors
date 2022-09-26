package com.phonepe.platform.rabbitmq.actor.test;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtlConfig {

    private boolean ttlEnabled;

    private Duration ttl = Duration.ofSeconds(1800);

}
