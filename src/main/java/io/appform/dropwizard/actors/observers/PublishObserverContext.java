package io.appform.dropwizard.actors.observers;

import com.rabbitmq.client.AMQP;
import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@Builder
public class PublishObserverContext {
    private final String queueName;
    private AMQP.BasicProperties properties;

    public void updateHeader(String key,Object value){
        Map<String, Object> newHeadersMap = getHeadersMap();
        newHeadersMap.put(key,value);
        this.properties = this.getBasicProperties(this.properties, newHeadersMap);
    }

    public void updateHeaders(Map<String,Object> headers){
        Map<String, Object> newHeadersMap = getHeadersMap();
        newHeadersMap.putAll(headers);
        this.properties = this.getBasicProperties(this.properties, newHeadersMap);
    }




    private Map<String, Object> getHeadersMap() {
        Map<String,Object> newHeadersMap = new HashMap<>();
        if(Objects.nonNull(this.properties.getHeaders())){
            newHeadersMap.putAll(this.properties.getHeaders());
        }
        return newHeadersMap;
    }


    private AMQP.BasicProperties getBasicProperties(AMQP.BasicProperties basicProperties,Map<String,Object> newHeadersMap) {
        return new AMQP.BasicProperties.Builder()
                .appId(basicProperties.getAppId())
                .clusterId(basicProperties.getClusterId())
                .contentEncoding(basicProperties.getContentEncoding())
                .contentType(basicProperties.getContentType())
                .correlationId(basicProperties.getCorrelationId())
                .deliveryMode(basicProperties.getDeliveryMode())
                .expiration(basicProperties.getExpiration())
                .messageId(basicProperties.getMessageId())
                .priority(basicProperties.getPriority())
                .replyTo(basicProperties.getReplyTo())
                .timestamp(basicProperties.getTimestamp())
                .type(basicProperties.getType())
                .userId(basicProperties.getUserId())
                .headers(newHeadersMap).build();
    }
}
