package com.phonepe.platform.rabbitmq.actor.test.tracing;

import io.opentracing.propagation.TextMap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static java.lang.String.valueOf;

public class HeadersMapExtractAdapter implements TextMap {

    private final Map<String, String> map = new HashMap<>();

    public HeadersMapExtractAdapter(Map<String, Object> headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            map.put(entry.getKey(), valueOf(entry.getValue()));
        }
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return map.entrySet().iterator();
    }

    @Override
    public void put(String key, String value) {
        throw new UnsupportedOperationException(
                "HeadersMapExtractAdapter should only be used with Tracer.extract()");
    }
}
