package io.github.mavenmcp.maven;

/**
 * Thrown when neither Maven Wrapper (mvnw) nor system Maven (mvn) can be found.
 */
public class MavenNotFoundException extends RuntimeException {

    public MavenNotFoundException(String message) {
        super(message);
    }
}
