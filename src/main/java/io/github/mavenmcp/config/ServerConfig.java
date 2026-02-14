package io.github.mavenmcp.config;

import java.nio.file.Path;

/**
 * Immutable server configuration created after successful startup validation.
 *
 * @param projectDir      validated project directory containing pom.xml
 * @param mavenExecutable detected Maven executable (mvnw or mvn)
 */
public record ServerConfig(Path projectDir, Path mavenExecutable) {

    public ServerConfig {
        if (projectDir == null) {
            throw new IllegalArgumentException("projectDir must not be null");
        }
        if (mavenExecutable == null) {
            throw new IllegalArgumentException("mavenExecutable must not be null");
        }
    }
}
