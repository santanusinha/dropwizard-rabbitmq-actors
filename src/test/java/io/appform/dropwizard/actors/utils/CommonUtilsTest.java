package io.appform.dropwizard.actors.utils;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommonUtilsTest {

    private static final String TEST_HEADER = "key";

    @Test
    public void testExtractMessagePropertiesHeader_withValidInput() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(TEST_HEADER, "value");
        Optional<String> result = CommonUtils.extractMessagePropertiesHeader(headers, TEST_HEADER, String.class);

        assertTrue(result.isPresent(), "Returned Optional should be present when a valid input is provided");
        assertEquals("value", result.get(), "Returned value does not match the expected value");
    }

    @Test
    public void testExtractMessagePropertiesHeader_withInvalidInput() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(TEST_HEADER, 123L);
        Optional<Long> result = CommonUtils.extractMessagePropertiesHeader(headers, TEST_HEADER, Long.class);

        assertFalse(result.isPresent(), "Returned Optional should be empty when an invalid input is provided");
        assertEquals(123L, result.get(), "Returned value does not match the expected value");
    }

    @Test
    public void testExtractMessagePropertiesHeader_withNullHeaders() {
        Optional<String> result = CommonUtils.extractMessagePropertiesHeader(null, TEST_HEADER, String.class);

        assertFalse(result.isPresent(), "Returned Optional should be empty when headers are null");
    }

    @Test
    public void testExtractMessagePropertiesHeader_withMissingKey() {
        Map<String, Object> headers = new HashMap<>();
        Optional<String> result = CommonUtils.extractMessagePropertiesHeader(headers, "missing key", String.class);

        assertFalse(result.isPresent(), "Returned Optional should be empty when the key is not present in headers");
    }
}