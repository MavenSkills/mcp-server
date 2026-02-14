package io.github.mavenmcp.maven;

/**
 * Thrown when a Maven process cannot be started or is interrupted.
 * Contains the duration elapsed before the failure for reporting purposes.
 */
public class MavenExecutionException extends RuntimeException {

    private final long duration;

    public MavenExecutionException(String message, Throwable cause, long duration) {
        super(message, cause);
        this.duration = duration;
    }

    /**
     * @return elapsed time in milliseconds before the failure occurred
     */
    public long getDuration() {
        return duration;
    }
}
