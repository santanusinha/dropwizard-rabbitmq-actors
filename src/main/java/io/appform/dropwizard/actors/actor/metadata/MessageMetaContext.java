package io.appform.dropwizard.actors.actor.metadata;

import java.util.Date;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Ref : <a href="https://www.rabbitmq.com/amqp-0-9-1-reference.html#class.basic">AMQP properties</a>
 */
@Data
@Builder
public class MessageMetaContext {

    private boolean redelivered;
    private String contentType;
    private String contentEncoding;
    private Map<String,Object> headers;
    private Integer deliveryMode;
    private Integer priority;
    private String correlationId;
    private String replyTo;
    private String expiration;
    private String messageId;
    private Date timestamp;
    private String type;
    private String userId;
    private String appId;
    private String clusterId;
}