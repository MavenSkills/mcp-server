package io.github.mavenmcp.parser;

import io.github.mavenmcp.model.TestFailure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deduplicates test failures that share the same root cause (identical message and stack trace).
 *
 * <p>Groups failures by {@code (message, stackTrace)} key, merging multiple identical failures
 * into a single entry with a consolidated {@code testMethod}, {@code testClass}, and
 * {@code testOutput} field. Singleton groups pass through unchanged.</p>
 */
public final class TestFailureDeduplicator {

    private static final int SUMMARY_THRESHOLD = 3;
    private static final String TEST_OUTPUT_SEPARATOR = "\n---\n";

    private TestFailureDeduplicator() {
    }

    /**
     * Deduplicate a list of test failures by grouping on {@code (message, stackTrace)}.
     *
     * @param failures the original list of test failures
     * @return deduplicated list preserving first-occurrence order
     */
    public static List<TestFailure> deduplicate(List<TestFailure> failures) {
        if (failures == null || failures.size() <= 1) {
            return failures;
        }

        Map<GroupKey, List<TestFailure>> groups = new LinkedHashMap<>();
        for (TestFailure failure : failures) {
            var key = new GroupKey(failure.message(), failure.stackTrace());
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
                group.stream().map(TestFailure::testMethod).collect(Collectors.toList()));
        String testClass = summarizeDistinct(
                group.stream().map(TestFailure::testClass).collect(Collectors.toList()));
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
        List<String> distinct = items.stream().distinct().collect(Collectors.toList());
        if (distinct.size() == 1) {
            return distinct.getFirst();
        }
        return summarize(distinct);
    }

    private static String mergeTestOutput(List<TestFailure> group) {
        List<String> outputs = group.stream()
                .map(TestFailure::testOutput)
                .filter(o -> o != null)
                .collect(Collectors.toList());
        if (outputs.isEmpty()) {
            return null;
        }
        return String.join(TEST_OUTPUT_SEPARATOR, outputs);
    }

    private record GroupKey(String message, String stackTrace) {
    }
}
