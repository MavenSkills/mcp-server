package io.github.mavenmcp.maven;

/**
 * Raw result of a Maven process execution.
 *
 * @param exitCode process exit code (0 = success)
 * @param stdout   complete captured standard output
 * @param stderr   complete captured standard error
 * @param duration wall-clock execution time in milliseconds
 */
public record MavenExecutionResult(int exitCode, String stdout, String stderr, long duration) {

    /**
     * @return true if Maven exited successfully (exit code 0)
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
