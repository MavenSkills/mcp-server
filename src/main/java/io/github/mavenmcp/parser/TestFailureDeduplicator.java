package io.github.mavenmcp.parser;

import io.github.mavenmcp.model.TestFailure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deduplicates test failures that share the same root cause.
 *
 * <p>Groups failures by extracting the deepest {@code Caused by:} line from the stack trace
 * (or the first line of message if no cause chain exists), merging identical groups
 * into a single entry with a consolidated {@code testMethod}, {@code testClass}, and
 * {@code testOutput} field. Singleton groups pass through unchanged.</p>
 */
public final class TestFailureDeduplicator {

    private static final int SUMMARY_THRESHOLD = 3;
    private static final String TEST_OUTPUT_SEPARATOR = "\n---\n";

    private TestFailureDeduplicator() {
    }

    /**
     * Deduplicate a list of test failures by grouping on root cause.
     * Root cause is the last {@code Caused by:} line from the stack trace,
     * or the first line of message if no Caused by chain exists.
     *
     * @param failures the original list of test failures
     * @return deduplicated list preserving first-occurrence order
     */
    public static List<TestFailure> deduplicate(List<TestFailure> failures) {
        if (failures == null || failures.size() <= 1) {
            return failures;
        }

        Map<String, List<TestFailure>> groups = new LinkedHashMap<>();
        for (TestFailure failure : failures) {
            var key = extractRootCauseKey(failure);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(failure);
        }

        if (groups.size() == failures.size()) {
            return failures;
        }

        List<TestFailure> result = new ArrayList<>(groups.size());
        for (List<TestFailure> group : groups.values()) {
            if (group.size() == 1) {
                result.add(group.getFirst());
            } else {
                result.add(merge(group));
            }
        }
        return result;
    }

    private static TestFailure merge(List<TestFailure> group) {
        TestFailure first = group.getFirst();

        String testMethod = summarize(
                group.stream().map(TestFailure::testMethod).toList());
        String testClass = summarizeDistinct(
                group.stream().map(TestFailure::testClass).toList());
        String testOutput = mergeTestOutput(group);

        return new TestFailure(testClass, testMethod, first.message(), first.stackTrace(), testOutput);
    }

    private static String summarize(List<String> items) {
        if (items.size() <= SUMMARY_THRESHOLD) {
            return String.join(", ", items);
        }
        List<String> first = items.subList(0, SUMMARY_THRESHOLD);
        return String.join(", ", first) + " (+" + (items.size() - SUMMARY_THRESHOLD) + " more)";
    }

    private static String summarizeDistinct(List<String> items) {
        List<String> distinct = items.stream().distinct().toList();
        if (distinct.size() == 1) {
            return distinct.getFirst();
        }
        return summarize(distinct);
    }

    private static String mergeTestOutput(List<TestFailure> group) {
        List<String> outputs = group.stream()
                .map(TestFailure::testOutput)
                .filter(Objects::nonNull)
                .toList();
        if (outputs.isEmpty()) {
            return null;
        }
        return String.join(TEST_OUTPUT_SEPARATOR, outputs);
    }

    /**
     * Extract the dedup key: last "Caused by:" line from stackTrace,
     * or first line of message if no Caused by chain.
     */
    static String extractRootCauseKey(TestFailure failure) {
        String stackTrace = failure.stackTrace();
        if (stackTrace != null) {
            String lastCausedBy = null;
            for (String line : stackTrace.split("\n")) {
                String stripped = line.strip();
                if (stripped.startsWith("Caused by:")) {
                    lastCausedBy = stripped;
                }
            }
            if (lastCausedBy != null) {
                return lastCausedBy;
            }
        }
        String message = failure.message();
        if (message != null) {
            int newline = message.indexOf('\n');
            return newline >= 0 ? message.substring(0, newline).strip() : message.strip();
        }
        return "";
    }
}
