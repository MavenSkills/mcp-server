package io.github.mavenmcp.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDetectMavenWrapperWhenPresentAndExecutable() throws IOException {
        Path mvnw = tempDir.resolve("mvnw");
        Files.writeString(mvnw, "#!/bin/sh\necho 'wrapper'");
        Files.setPosixFilePermissions(mvnw, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
        ));

        Path detected = MavenDetector.detect(tempDir);

        assertThat(detected).isEqualTo(mvnw);
    }

    @Test
    void shouldFallbackToSystemMvnWhenNoWrapper() {
        // tempDir has no mvnw, but system mvn should be available in test environment
        Path detected = MavenDetector.detect(tempDir);

        assertThat(detected.toString()).endsWith("mvn");
    }

    @Test
    void shouldFallbackToSystemMvnWhenWrapperNotExecutable() throws IOException {
        Path mvnw = tempDir.resolve("mvnw");
        Files.writeString(mvnw, "#!/bin/sh\necho 'wrapper'");
        // Do NOT set executable permission
        Files.setPosixFilePermissions(mvnw, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
        ));

        Path detected = MavenDetector.detect(tempDir);

        // Should fall back to system mvn, not the non-executable wrapper
        assertThat(detected).isNotEqualTo(mvnw);
        assertThat(detected.toString()).endsWith("mvn");
    }

    @Test
    void shouldPreferWrapperOverSystemMvn() throws IOException {
        Path mvnw = tempDir.resolve("mvnw");
        Files.writeString(mvnw, "#!/bin/sh\necho 'wrapper'");
        Files.setPosixFilePermissions(mvnw, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
        ));

        Path detected = MavenDetector.detect(tempDir);

        // Should prefer the wrapper over system mvn
        assertThat(detected).isEqualTo(mvnw);
    }
}
