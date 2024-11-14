package io.appform.dropwizard.actors.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class YamlReader {

    // Config reader
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @SneakyThrows
    public <T> T readYAML(final String data, final TypeReference<T> typeReference) {
        return yamlMapper.readValue(data, typeReference);
    }

    @SneakyThrows
    public <T> T loadConfig(final String filePath, final TypeReference<T> typeReference) {
        String data = fixture(filePath);
        return readYAML(data, typeReference);
    }

    public static String fixture(String filename) {
        return fixture(filename, StandardCharsets.UTF_8);
    }

    private static String fixture(String filename, Charset charset) {
        URL resource = Resources.getResource(filename);

        try {
            return Resources.toString(resource, charset).trim();
        } catch (IOException var4) {
            IOException e = var4;
            throw new IllegalArgumentException(e);
        }
    }


}
