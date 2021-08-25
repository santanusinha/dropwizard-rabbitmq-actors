package io.appform.dropwizard.actors.compression;

import java.io.IOException;

public interface CompressionAlgorithmVisitor<T> {

    T visitGzip() throws IOException;

    T visitDeflate() throws IOException;
}
