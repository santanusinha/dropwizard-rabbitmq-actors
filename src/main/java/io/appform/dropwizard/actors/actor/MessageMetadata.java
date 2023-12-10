package io.appform.dropwizard.actors.actor;

import java.util.Date;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class MessageMetadata {

    private String contentType;
    private String contentEncoding;
    private Map<String,Object> headers;
    private Integer deliveryMode;
    private Integer priority;
    private String correlationId;
    private String replyTo;
    private String expiration;
    private String messageId;
    private Date msgSentTimestamp;
    private String type;
    private String userId;
    private String appId;
    private String clusterId;

    // custom fields
    private boolean redelivered;
    private long delayInMs;
    private boolean expired;
}
