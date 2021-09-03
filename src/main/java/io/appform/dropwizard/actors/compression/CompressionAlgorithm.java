package io.appform.dropwizard.actors.compression;

import java.io.IOException;

public enum CompressionAlgorithm {

    GZIP {
        @Override
        public <T> T visit(CompressionAlgorithmVisitor<T> compressionAlgorithmVisitor) throws IOException {
            return compressionAlgorithmVisitor.visitGzip();
        }
    },

    DEFLATE{
        @Override
        public <T> T visit(CompressionAlgorithmVisitor<T> compressionAlgorithmVisitor) throws IOException {
            return compressionAlgorithmVisitor.visitDeflate();
        }
    };

    public abstract <T> T visit(CompressionAlgorithmVisitor<T> compressionAlgorithmVisitor) throws IOException;
}
