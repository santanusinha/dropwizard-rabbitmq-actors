package io.appform.dropwizard.actors.retry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEP-01: pom.xml structural conventions for the failsafe dependency.
 * DEP-02/DEP-03/ENG-05: pom.xml must not reference guava-retrying or
 * com.github.rholder. Reads pom.xml as a string and asserts on its content,
 * matching the grep-gate test idiom (no DOM parsing).
 */
class PomStructureTest {

    private static final Path POM = Path.of("pom.xml");

    @Test
    void failsafeVersionPropertyPresent() {
        String pom = read(POM);
        assertTrue(pom.contains("<failsafe.version>3.3.2</failsafe.version>"),
                "pom.xml must declare <failsafe.version>3.3.2</failsafe.version> property");
    }

    @Test
    void failsafeDependencyUsesPropertyVersionAndNoScopeTag() {
        String pom = read(POM);
        int idx = pom.indexOf("<groupId>dev.failsafe</groupId>");
        assertTrue(idx >= 0, "pom.xml must contain a dev.failsafe dependency block");
        // window covering the failsafe <dependency> block (groupId through closing </dependency>)
        int blockEnd = pom.indexOf("</dependency>", idx);
        assertTrue(blockEnd >= 0, "failsafe dependency block must be closed");
        String block = pom.substring(idx, blockEnd);
        assertTrue(block.contains("<version>${failsafe.version}</version>"),
                "failsafe dependency must use <version>${failsafe.version}</version>");
        assertFalse(block.contains("<scope>"),
                "failsafe dependency must omit <scope> (compile scope by omission, project convention)");
    }

    @Test
    void noDeprecatedNetJodahGroupId() {
        String pom = read(POM);
        assertFalse(pom.contains("net.jodah"),
                "pom.xml must not reference the deprecated net.jodah groupId");
    }

    @Test
    void noGuavaRetryingDependencyInPom() {
        String pom = read(POM);
        assertFalse(pom.contains("guava-retrying"),
                "pom.xml must not contain a guava-retrying dependency block");
    }

    @Test
    void noGuavaRetryingVersionPropertyInPom() {
        String pom = read(POM);
        assertFalse(pom.contains("guava-retrying.version"),
                "pom.xml must not declare a guava-retrying.version property");
    }

    @Test
    void noGuavaRetryingRefsInPom() {
        String pom = read(POM);
        assertFalse(pom.contains("com.github.rholder"),
                "pom.xml must not reference com.github.rholder (guava-retrying groupId)");
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
