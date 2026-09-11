package io.appform.dropwizard.actors.retry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * VER-04: Source pattern enforcement via Files.walk. Fails if forbidden API
 * patterns appear in the retry package source.
 */
class RetryGrepGateTest {

    private static final Path RETRY_DIR = Path.of("src/main/java/io/appform/dropwizard/actors/retry");

    @Test
    void noWithMaxRetriesExceptNegativeOne() throws IOException {
        try (Stream<Path> files = Files.walk(RETRY_DIR)) {
            files
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        String content = read(p);
                        String withoutNegativeOne = content.replaceAll("withMaxRetries\\(-1\\)", "");
                        assertFalse(withoutNegativeOne.contains("withMaxRetries"),
                                "withMaxRetries found in " + p + " — use withMaxAttempts instead (except -1 for time-limited)");
                    });
        }
    }

    @Test
    void noAsyncCallsInRetryPackage() throws IOException {
        try (Stream<Path> files = Files.walk(RETRY_DIR)) {
            files
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        String content = read(p);
                        assertFalse(content.contains("getAsync("),
                                "getAsync found in " + p + " — synchronous .get() only");
                        assertFalse(content.contains("runAsync("),
                                "runAsync found in " + p + " — synchronous .get() only");
                    });
        }
    }

    @Test
    void noGuavaRetryingImportsInRetryPackage() throws IOException {
        try (Stream<Path> files = Files.walk(RETRY_DIR)) {
            files
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        String content = read(p);
                        assertFalse(content.contains("com.github.rholder"),
                                "com.github.rholder import found in " + p + " — guava-retrying must not be referenced");
                    });
        }
    }

    @Test
    void noHandleResultInRetryPackage() throws IOException {
        // PAR-09: Boolean return is a success signal; handleResult(false) must not appear
        try (Stream<Path> files = Files.walk(RETRY_DIR)) {
            files
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        String content = read(p);
                        assertFalse(content.contains("handleResult"),
                                "handleResult found in " + p + " — false return must be a success signal, not a retry trigger");
                    });
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
