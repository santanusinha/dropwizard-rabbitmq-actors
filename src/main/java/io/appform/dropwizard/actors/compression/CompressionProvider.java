package io.appform.dropwizard.actors.compression;

import org.apache.commons.jcs.utils.zip.CompressionUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class CompressionProvider {

    public byte[] compress(byte[] data, CompressionAlgorithm compressionAlgorithm) throws IOException {
        return compressionAlgorithm.visit(new CompressionAlgorithmVisitor<byte[]>() {
            @Override
            public byte[] visitGzip() throws IOException {
                return compressUsingGzip(data);
            }

            @Override
            public byte[] visitDeflate() throws IOException {
                return CompressionUtil.compressByteArray(data);
            }
        });
    }

    public byte[] decompress(byte[] data, CompressionAlgorithm compressionAlgorithm) throws IOException {
        return compressionAlgorithm.visit(new CompressionAlgorithmVisitor<byte[]>() {
            @Override
            public byte[] visitGzip() throws IOException {
                return CompressionUtil.decompressGzipByteArray(data);
            }

            @Override
            public byte[] visitDeflate() {
                return CompressionUtil.decompressByteArray(data);
            }
        });
    }

    private byte[] compressUsingGzip(byte[] input) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);
        GZIPOutputStream gzip = new GZIPOutputStream(bos);
        gzip.write(input);
        gzip.close();
        byte[] compressed = bos.toByteArray();
        bos.close();
        return compressed;
    }

}
