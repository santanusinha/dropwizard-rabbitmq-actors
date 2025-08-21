package io.appform.dropwizard.actors.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@AllArgsConstructor
@Jacksonized
@Builder
public class TestShardedMessage {
    public enum Type {
        SHARDED_MESSAGE
    }
    int shardId;
}
