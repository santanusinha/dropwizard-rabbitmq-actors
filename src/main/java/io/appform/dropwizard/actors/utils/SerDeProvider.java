package io.appform.dropwizard.actors.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SerDeProvider {

    private final ObjectMapper mapper;

    public SerDeProvider(ObjectMapper mapper) {
        this.mapper =  mapper;
    }

    public <T> T deserialize(final byte[] data,
                             final Class<T> valueType) {
        if (data == null) {
            return null;
        }
        try {
            return mapper.readValue(data, valueType);
        } catch (Exception e) {
            throw new RuntimeException("ERROR in Deserializing", e);
        }
    }


    @SuppressWarnings({"java:S1168"})
    public byte[] serialize(final Object data) {
        if (data == null) {
            return null;
        }
        try {
            return mapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("ERROR in serializing", e);
        }
    }

    public <T> T clone(final T object,
                       final Class<T> clazz) {
        return deserialize(serialize(object), clazz);
    }

}
