package com.phonepe.platform.tracing;

import io.opentracing.propagation.TextMap;

import java.util.Iterator;
import java.util.Map;

public class HeadersMapInjectAdapter implements TextMap {

    private final Map<String, Object> headers;

    public HeadersMapInjectAdapter(Map<String, Object> headers) {
        this.headers = headers;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        throw new UnsupportedOperationException("iterator should never be used with Tracer.inject()");
    }

    @Override
    public void put(String key, String value) {
        headers.put(key, value);
    }
}
