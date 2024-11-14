package io.appform.dropwizard.actors.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MapperUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    public <T> T deserialize(final byte[] data,
                             final Class<T> valueType) {
        if (data == null) {
            return null;
        }
        try {
            return mapper.readValue(data, valueType);
        } catch (Exception e) {
            throw new RuntimeException("ERROR in Deserilizing", e);
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
            throw new RuntimeException("ERROR in serilizing", e);
        }
    }

    public <T> T clone(final T object,
                       final Class<T> clazz) {
        return deserialize(serialize(object), clazz);
    }

}
