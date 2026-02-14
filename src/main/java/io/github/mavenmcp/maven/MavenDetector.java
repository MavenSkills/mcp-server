package io.github.mavenmcp.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects the Maven executable to use for a project.
 * Prefers ./mvnw (Maven Wrapper), falls back to system mvn on PATH.
 */
public final class MavenDetector {

    private static final Logger log = LoggerFactory.getLogger(MavenDetector.class);

    private MavenDetector() {
        // utility class
    }

    /**
     * Detect the Maven executable for the given project directory.
     *
     * @param projectDir the project directory to check for mvnw
     * @return the path to the Maven executable
     * @throws MavenNotFoundException if neither mvnw nor mvn is available
     */
    public static Path detect(Path projectDir) {
        // 1. Check for Maven Wrapper in project directory
        Path mvnw = projectDir.resolve("mvnw");
        if (Files.isRegularFile(mvnw) && Files.isExecutable(mvnw)) {
            log.debug("Found Maven Wrapper at {}", mvnw);
            return mvnw;
        }

        // 2. Fallback to system mvn on PATH
        Path systemMvn = findSystemMvn();
        if (systemMvn != null) {
            log.debug("Using system Maven at {}", systemMvn);
            return systemMvn;
        }

        throw new MavenNotFoundException(
                "Maven not found. Install Maven or add mvnw to your project.");
    }

    private static Path findSystemMvn() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "mvn");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();
            if (exitCode == 0 && !output.isEmpty()) {
                return Path.of(output);
            }
        } catch (IOException | InterruptedException e) {
            log.debug("Failed to locate system mvn: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }
}
