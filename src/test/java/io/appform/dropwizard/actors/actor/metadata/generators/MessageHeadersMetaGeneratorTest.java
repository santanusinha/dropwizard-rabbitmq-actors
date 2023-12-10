package io.appform.dropwizard.actors.actor.metadata.generators;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.dropwizard.actors.actor.metadata.MessageMetaContext;
import java.util.Collections;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.Test;

public class MessageHeadersMetaGeneratorTest {

    @Test
    void shouldSetHeaders() {
        val testKey = "testKey";
        val testValue = "testValue";
        Map<String, Object> headers = Collections.singletonMap(testKey, testValue);
        val messageMetaContext = MessageMetaContext.builder().headers(headers).build();

        val messageMetadata = MessageMetadata.builder().build();
        val generator = new MessageHeadersMetaGenerator();
        generator.generate(messageMetaContext, messageMetadata);

        val resultHeaders = messageMetadata.getHeaders();
        assertNotNull(resultHeaders, "Headers should be present");
        assertFalse(resultHeaders.isEmpty(), "Headers should be present");
        assertEquals(testValue, resultHeaders.get(testKey), "Header value should match with provided one");
    }

}