package io.github.mavenmcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single compilation error or warning extracted from Maven/javac output.
 *
 * @param file     source file path relative to project root
 * @param line     line number (1-based)
 * @param column   column number (1-based), null if not available
 * @param message  error or warning message
 * @param severity ERROR or WARNING
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompilationError(
        String file,
        int line,
        Integer column,
        String message,
        String severity
) {
}
