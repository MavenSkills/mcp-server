package io.github.mavenmcp.model;

/**
 * Aggregated test execution summary.
 *
 * @param testsRun     total tests executed
 * @param testsFailed  tests with assertion failures
 * @param testsSkipped skipped tests
 * @param testsErrored tests with unexpected errors
 */
public record TestSummary(int testsRun, int testsFailed, int testsSkipped, int testsErrored) {
}
