package io.appform.dropwizard.actors.connectivity.actor.base.helper;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.base.helper.MessageBodyHelper;
import io.appform.dropwizard.actors.compression.CompressionAlgorithm;
import io.appform.dropwizard.actors.utils.MessageHeaders;
import lombok.val;
import org.junit.Assert;
import org.junit.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

public class MessageBodyHelperTest {

    private MessageBodyHelper messageBodyHelper = new MessageBodyHelper();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testCompressMessage() throws IOException {
        val list = new ArrayList<>();
        IntStream.range(0, 100)
                .forEach(t -> list.add(UUID.randomUUID().toString()));

        val messageBytes = objectMapper.writeValueAsBytes(list);
        Map<String, Object> headers = ImmutableMap.of(MessageHeaders.COMPRESSION_TYPE, CompressionAlgorithm.GZIP);
        val compressionResponse = messageBodyHelper.compressMessage(messageBytes, new AMQP.BasicProperties.Builder()
                .headers(headers).build());

        Assert.assertTrue(compressionResponse.length  < messageBytes.length);
    }

    @Test
    public void testCompressMessageWithoutCompressionHeader() throws IOException {
        val list = new ArrayList<>();
        IntStream.range(0, 100)
                .forEach(t -> list.add(UUID.randomUUID().toString()));

        val messageBytes = objectMapper.writeValueAsBytes(list);
        val compressionResponse = messageBodyHelper.compressMessage(messageBytes, new AMQP.BasicProperties.Builder().build());

        Assert.assertEquals(messageBytes.length, compressionResponse.length);
    }

    @Test
    public void testDecompressMessage() throws IOException {
        val list = new ArrayList<>();
        IntStream.range(0, 100)
                .forEach(t -> list.add(UUID.randomUUID().toString()));

        val messageBytes = objectMapper.writeValueAsBytes(list);
        Map<String, Object> headers = ImmutableMap.of(MessageHeaders.COMPRESSION_TYPE, CompressionAlgorithm.GZIP);

        val compressionResponse = messageBodyHelper.compressMessage(messageBytes, new AMQP.BasicProperties.Builder()
                .headers(headers).build());

        val decompressionResponse = messageBodyHelper.decompressMessage(compressionResponse, new AMQP.BasicProperties.Builder()
                .headers(headers).build());

        Assert.assertTrue(decompressionResponse.length  > compressionResponse.length);
        Assert.assertArrayEquals(decompressionResponse, messageBytes);
    }

    @Test
    public void testDecompressMessageWithoutCompressionHeader() throws IOException {
        val list = new ArrayList<>();
        IntStream.range(0, 100)
                .forEach(t -> list.add(UUID.randomUUID().toString()));

        val messageBytes = objectMapper.writeValueAsBytes(list);
        val compressionResponse = messageBodyHelper.compressMessage(messageBytes, new AMQP.BasicProperties.Builder().build());
        val decompressionResponse = messageBodyHelper.decompressMessage(messageBytes, new AMQP.BasicProperties.Builder().build());

        Assert.assertArrayEquals(decompressionResponse, messageBytes);
        Assert.assertArrayEquals(compressionResponse, messageBytes);
    }

}
