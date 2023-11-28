package io.appform.dropwizard.actors.actor.metadata.generators;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;
import io.appform.dropwizard.actors.actor.metadata.MessageMetadataProvider;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MessageMetadataProviderTest {

    private static final String CUSTOM_HEADER_KEY = "customHeaderKey";
    private static final String CUSTOM_HEADER_VALUE = "value1234";

    @Mock
    private Envelope mockEnvelope;
    @Mock
    private AMQP.BasicProperties mockProperties;

    private MessageMetadataProvider messageMetadataProvider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        List<String> messageMetaGeneratorClasses = Arrays.asList(CustomMessageMetaHeaderGenerator.class.getName());
        messageMetadataProvider = new MessageMetadataProvider(messageMetaGeneratorClasses);
    }

    @Test
    void shouldCreateBlankMessageMetadataWhenMessageMetaGeneratorClassesIsNull() {
        MessageMetadataProvider messageMetadataProvider = new MessageMetadataProvider(null);
        val messageMetadata = messageMetadataProvider.createMetadata(mockEnvelope, mockProperties);
        assertNotNull(messageMetadata);

        assertFalse(messageMetadata.isRedelivered());
        assertEquals(0, messageMetadata.getDelayInMs());
        assertFalse(messageMetadata.isExpired());

        assertNull(messageMetadata.getContentType());
        assertNull(messageMetadata.getContentEncoding());
        assertNull(messageMetadata.getHeaders());
        assertNull(messageMetadata.getDeliveryMode());
        assertNull(messageMetadata.getPriority());
        assertNull(messageMetadata.getCorrelationId());
        assertNull(messageMetadata.getReplyTo());
        assertNull(messageMetadata.getExpiration());
        assertNull(messageMetadata.getMessageId());
        assertNull(messageMetadata.getMsgSentTimestamp());
        assertNull(messageMetadata.getType());
        assertNull(messageMetadata.getUserId());
        assertNull(messageMetadata.getAppId());
        assertNull(messageMetadata.getClusterId());
    }

    @Test
    void shouldGenerateMetadataWithCustomMessageMetaHeader() {
        val messageMetadata = messageMetadataProvider.createMetadata(mockEnvelope, mockProperties);

        assertNotNull(messageMetadata.getHeaders(), "Headers shouldn't be null");
        assertEquals(CUSTOM_HEADER_VALUE, messageMetadata.getHeaders().get(CUSTOM_HEADER_KEY));
    }

    @Test
    void shouldThrowExceptionWhenEnvelopeIsNull() {
        val exception = assertThrows(NullPointerException.class,
                () -> messageMetadataProvider.createMetadata(null, mockProperties));

        assertEquals("Envelope should not be null", exception.getMessage(),
                "Exception message should match");
    }

    @Test
    void shouldThrowExceptionWhenPropertiesIsNull() {
        val exception = assertThrows(NullPointerException.class,
                () -> messageMetadataProvider.createMetadata(mockEnvelope, null));

        assertEquals("AMQP.BasicProperties should not be null", exception.getMessage(),
                "Exception message should match");
    }

    public static class CustomMessageMetaHeaderGenerator implements MessageMetadataGenerator {

        @Override
        public void generate(MessageMetaContext messageMetaContext, MessageMetadata messageMetadata) {
            Map<String, Object> headers = messageMetaContext.getHeaders();
            if (null == headers) {
                headers = new HashMap<>();
            }
            headers.put(CUSTOM_HEADER_KEY, CUSTOM_HEADER_VALUE);
            messageMetadata.setHeaders(headers);
        }
    }
}