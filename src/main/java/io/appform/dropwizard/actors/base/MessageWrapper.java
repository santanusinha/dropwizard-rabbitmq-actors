package io.appform.dropwizard.actors.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageWrapper<Message> {
    private Message message;
    private long publishTimeStamp;
}
