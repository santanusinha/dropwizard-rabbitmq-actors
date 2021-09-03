package io.appform.dropwizard.actors.base.helper;

import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.compression.CompressionAlgorithm;
import io.appform.dropwizard.actors.compression.CompressionProvider;

import java.io.IOException;

import static io.appform.dropwizard.actors.utils.MessageHeaders.COMPRESSION_TYPE;

public class MessageBodyHelper {

    private CompressionProvider compressionProvider = new CompressionProvider();
    public MessageBodyHelper() {
    }

    public byte[] decompressMessage(final byte[] message,
                                    final AMQP.BasicProperties properties) throws IOException {

        if (properties.getHeaders() != null && properties.getHeaders().containsKey(COMPRESSION_TYPE)) {
            return compressionProvider.decompress(message, (CompressionAlgorithm) properties.getHeaders()
                    .get(COMPRESSION_TYPE));
        }
        return message;
    }

    public byte[] compressMessage(final byte[] message,
                                  final AMQP.BasicProperties properties) throws IOException {

        if (properties.getHeaders() != null && properties.getHeaders().containsKey(COMPRESSION_TYPE)) {
            return compressionProvider.compress(message, (CompressionAlgorithm) properties.getHeaders()
                    .get(COMPRESSION_TYPE));
        }
        return message;
    }
}
