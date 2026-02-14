package io.github.mavenmcp.config;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateConfigWithValidPaths() {
        Path mavenExe = tempDir.resolve("mvn");
        var config = new ServerConfig(tempDir, mavenExe);

        assertThat(config.projectDir()).isEqualTo(tempDir);
        assertThat(config.mavenExecutable()).isEqualTo(mavenExe);
    }

    @Test
    void shouldRejectNullProjectDir() {
        assertThatThrownBy(() -> new ServerConfig(null, Path.of("/usr/bin/mvn")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectDir");
    }

    @Test
    void shouldRejectNullMavenExecutable() {
        assertThatThrownBy(() -> new ServerConfig(tempDir, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mavenExecutable");
    }
}
