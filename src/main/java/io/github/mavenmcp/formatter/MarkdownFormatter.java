package io.github.mavenmcp.formatter;

import io.github.mavenmcp.model.BuildResult;
import io.github.mavenmcp.model.CompilationError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Formats BuildResult as a Markdown string for LLM consumption.
 * Pure function — no side effects, no dependencies beyond the model.
 */
public final class MarkdownFormatter {

    private MarkdownFormatter() {}

    /**
     * Formats a BuildResult as a Markdown string.
     *
     * @param result    the build result to format
     * @param operation "Clean", "Compile", or "Test"
     * @return Markdown-formatted string
     */
    public static String format(BuildResult result, String operation) {
        var sb = new StringBuilder();
        appendHeader(sb, result, operation);
        appendErrors(sb, result);
        appendFailures(sb, result);
        appendRawOutput(sb, result);
        appendNote(sb, result);
        return sb.toString().stripTrailing();
    }

    private static void appendHeader(StringBuilder sb, BuildResult result, String operation) {
        sb.append(operation).append(' ').append(result.status())
                .append(" (").append(formatDuration(result.duration())).append(')');

        if (result.summary() != null) {
            var s = result.summary();
            sb.append(" — ").append(s.testsRun()).append(" run, ")
                    .append(s.testsFailed()).append(" failed");
            if (s.testsSkipped() > 0) {
                sb.append(", ").append(s.testsSkipped()).append(" skipped");
            }
        } else if (!isNullOrEmpty(result.errors())) {
            sb.append(" — ").append(pluralize(result.errors().size(), "error"));
        } else if (!isNullOrEmpty(result.warnings())) {
            sb.append(" — ").append(pluralize(result.warnings().size(), "warning"));
        }
    }

    private static void appendErrors(StringBuilder sb, BuildResult result) {
        if (isNullOrEmpty(result.errors())) return;

        var byFile = new LinkedHashMap<String, List<CompilationError>>();
        for (var error : result.errors()) {
            byFile.computeIfAbsent(error.file(), k -> new ArrayList<>()).add(error);
        }

        for (var entry : byFile.entrySet()) {
            sb.append("\n\n### ").append(entry.getKey());
            for (var error : entry.getValue()) {
                sb.append("\n- L").append(error.line());
                if (error.column() != null) {
                    sb.append(':').append(error.column());
                }
                sb.append(" — ").append(error.message());
            }
        }
    }

    private static void appendFailures(StringBuilder sb, BuildResult result) {
        if (isNullOrEmpty(result.failures())) return;

        for (var failure : result.failures()) {
            String shortClass = shortClassName(failure.testClass());
            String method = failure.testMethod() != null ? failure.testMethod() : "unknown";
            sb.append("\n\n### FAILED: ").append(shortClass).append('#').append(method);
            if (failure.message() != null) {
                sb.append('\n').append(failure.message());
            }
            if (failure.stackTrace() != null && !failure.stackTrace().isBlank()) {
                appendIndented(sb, failure.stackTrace());
            }
            if (failure.testOutput() != null && !failure.testOutput().isBlank()) {
                sb.append("\n  Test output:");
                appendIndented(sb, failure.testOutput());
            }
        }
    }

    private static void appendRawOutput(StringBuilder sb, BuildResult result) {
        if (result.output() == null || result.output().isBlank()) return;
        // Skip raw output when structured errors or failures are present
        if (!isNullOrEmpty(result.errors()) || !isNullOrEmpty(result.failures())) return;
        sb.append("\n");
        appendIndented(sb, result.output());
    }

    private static void appendNote(StringBuilder sb, BuildResult result) {
        if (result.note() == null || result.note().isBlank()) return;
        sb.append("\n\n> ").append(result.note());
    }

    private static void appendIndented(StringBuilder sb, String text) {
        for (String line : text.split("\n")) {
            sb.append("\n  ").append(line);
        }
    }

    private static boolean isNullOrEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private static String pluralize(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static String shortClassName(String fqcn) {
        if (fqcn == null) return "Unknown";
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    private static String formatDuration(long millis) {
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
    }
}
