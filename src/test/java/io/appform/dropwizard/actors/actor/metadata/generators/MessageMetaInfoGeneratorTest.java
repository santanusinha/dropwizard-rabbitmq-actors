package io.appform.dropwizard.actors.actor.metadata.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;
import java.util.Date;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageMetaInfoGeneratorTest {

    private MessageMetaContext messageMetaContext;

    @BeforeEach
    void setUp() {
        messageMetaContext = MessageMetaContext.builder()
                .contentEncoding("gzip")
                .contentType("application/json")
                .deliveryMode(2)
                .priority(1)
                .correlationId("123456")
                .replyTo("reply")
                .expiration("60")
                .messageId("msgId")
                .timestamp(new Date())
                .type("type")
                .userId("usr")
                .appId("app")
                .clusterId("cluster")
                .build();
    }

    @Test
    void shouldGenerateMetadata() {
        val messageMetadata = MessageMetadata.builder().build();
        val generator = new MessageMetaInfoGenerator();
        generator.generate(messageMetaContext, messageMetadata);

        assertEquals("gzip", messageMetadata.getContentEncoding(), "Content encoding should match");
        assertEquals("application/json", messageMetadata.getContentType(), "Content type should match");
        assertEquals(2, messageMetadata.getDeliveryMode(), "Delivery mode should match");
        assertEquals(1, messageMetadata.getPriority(), "Priority should match");
        assertEquals("123456", messageMetadata.getCorrelationId(), "Correlation ID should match");
        assertEquals("reply", messageMetadata.getReplyTo(), "Reply-to should match");
        assertEquals("60", messageMetadata.getExpiration(), "Expiration should match");
        assertEquals("msgId", messageMetadata.getMessageId(), "Message ID should match");
        assertEquals(messageMetaContext.getTimestamp(), messageMetadata.getMsgSentTimestamp(),
                "Timestamp should match");
        assertEquals("type", messageMetadata.getType(), "Message type should match");
        assertEquals("usr", messageMetadata.getUserId(), "User ID should match");
        assertEquals("app", messageMetadata.getAppId(), "App ID should match");
        assertEquals("cluster", messageMetadata.getClusterId(), "Cluster ID should match");
    }

    @Test
    void shouldGenerateDefaultMetadata() {
        val messageMetaContext = MessageMetaContext.builder().build();
        val messageMetadata = MessageMetadata.builder().build();
        val generator = new MessageMetaInfoGenerator();
        generator.generate(messageMetaContext, messageMetadata);

        assertNull(messageMetadata.getContentEncoding(), "Content encoding should be null");
        assertNull(messageMetadata.getContentType(), "Content type should be null");
        assertNull(messageMetadata.getDeliveryMode(), "Delivery mode should be null");
        assertNull(messageMetadata.getPriority(), "Priority should be null");
        assertNull(messageMetadata.getCorrelationId(), "Correlation ID should be null");
        assertNull(messageMetadata.getReplyTo(), "Reply-to should be null");
        assertNull(messageMetadata.getExpiration(), "Expiration should be null");
        assertNull(messageMetadata.getMessageId(), "Message ID should be null");
        assertNull(messageMetadata.getMsgSentTimestamp(), "Timestamp should be null");
        assertNull(messageMetadata.getType(), "Message type should be null");
        assertNull(messageMetadata.getUserId(), "User ID should be null");
        assertNull(messageMetadata.getAppId(), "App ID should be null");
        assertNull(messageMetadata.getClusterId(), "Cluster ID should be null");
    }

}