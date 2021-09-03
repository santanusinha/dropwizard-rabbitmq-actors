package io.appform.dropwizard.actors.connectivity.actor.compression;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.dropwizard.actors.compression.CompressionAlgorithm;
import io.appform.dropwizard.actors.compression.CompressionProvider;
import lombok.val;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.IntStream;

public class CompressionProviderTest {

    private CompressionProvider compressionProvider = new CompressionProvider();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testGzip() throws IOException {
        testUtil(CompressionAlgorithm.GZIP);
    }

    @Test
    public void testDeflate() throws IOException {
        testUtil(CompressionAlgorithm.DEFLATE);
    }

    private void testUtil(final CompressionAlgorithm compressionAlgorithm) throws IOException {
        val list = new ArrayList<>();
        IntStream.range(0, 100)
                .forEach(t -> list.add(UUID.randomUUID().toString()));

        val messageBytes = objectMapper.writeValueAsBytes(list);
        val compressionResponse = compressionProvider.compress(messageBytes, compressionAlgorithm);

        Assert.assertTrue(compressionResponse.length  < messageBytes.length);

        val decompressionResponse = compressionProvider.decompress(compressionResponse, compressionAlgorithm);
        Assert.assertArrayEquals(decompressionResponse, messageBytes);
    }
}
