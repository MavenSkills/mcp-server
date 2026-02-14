package io.github.mavenmcp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Top-level response returned by all Maven MCP tools.
 * Null fields are omitted from JSON serialization to keep responses compact.
 *
 * @param status   SUCCESS, FAILURE, or TIMEOUT
 * @param duration wall-clock time of Maven execution in milliseconds
 * @param errors   compilation errors (severity=ERROR), null if not applicable
 * @param warnings compilation warnings (severity=WARNING), null if not applicable
 * @param summary  test execution summary, null for non-test tools
 * @param failures individual test failures, null for non-test tools
 * @param artifact built artifact info, null unless maven_package succeeds
 * @param output   raw Maven output, only populated on FAILURE
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildResult(
        String status,
        long duration,
        List<CompilationError> errors,
        List<CompilationError> warnings,
        TestSummary summary,
        List<TestFailure> failures,
        Object artifact,
        String output
) {

    /** Status constants */
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";
    public static final String TIMEOUT = "TIMEOUT";
}
