package io.github.mavenmcp.parser;

import io.github.mavenmcp.model.TestFailure;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFailureDeduplicatorTest {

    private static final String TRACE_WITH_ROOT_CAUSE_A = """
            java.lang.IllegalStateException: context failed
            \tat org.framework.Runner.run(Runner.java:50)
            Caused by: java.net.ConnectException: Connection refused""";

    private static final String TRACE_WITH_ROOT_CAUSE_B = """
            java.lang.IllegalStateException: context failed
            \tat org.framework.Runner.run(Runner.java:50)
            Caused by: java.lang.OutOfMemoryError: heap space""";

    private static final String TRACE_NO_CAUSED_BY = """
            org.opentest4j.AssertionFailedError: expected:<200> but was:<404>
            \tat com.example.FooTest.testA(FooTest.java:42)""";

    private static TestFailure failure(String testClass, String testMethod, String message,
                                       String stackTrace, String testOutput) {
        return new TestFailure(testClass, testMethod, message, stackTrace, testOutput);
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(TestFailureDeduplicator.deduplicate(null)).isNull();
    }

    @Test
    void shouldReturnSingleFailureUnchanged() {
        var f = failure("com.example.FooTest", "testA", "error", TRACE_WITH_ROOT_CAUSE_A, "output");
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isSameAs(f);
    }

    @Test
    void shouldPassThroughUniqueFailuresUnchanged() {
        var f1 = failure("com.example.FooTest", "testA", "error A", TRACE_WITH_ROOT_CAUSE_A, null);
        var f2 = failure("com.example.BarTest", "testB", "error B", TRACE_WITH_ROOT_CAUSE_B, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isSameAs(f1);
        assertThat(result.get(1)).isSameAs(f2);
    }

    @Test
    void shouldGroupFailuresWithSameRootCause() {
        var f1 = failure("com.example.FooTest", "testA", "error", TRACE_WITH_ROOT_CAUSE_A, null);
        var f2 = failure("com.example.FooTest", "testB", "error", TRACE_WITH_ROOT_CAUSE_A, null);
        var f3 = failure("com.example.FooTest", "testC", "error", TRACE_WITH_ROOT_CAUSE_A, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testMethod()).isEqualTo("testA, testB, testC");
    }

    @Test
    void shouldGroupByRootCauseNotFullMessage() {
        // Different messages (e.g. differing by object hash) but same root cause
        String trace1 = "java.lang.IllegalStateException: context for [Config@aaa]\n"
                + "\tat org.framework.Runner.run(Runner.java:50)\n"
                + "Caused by: java.net.ConnectException: Connection refused";
        String trace2 = "java.lang.IllegalStateException: context for [Config@bbb]\n"
                + "\tat org.framework.Runner.run(Runner.java:50)\n"
                + "Caused by: java.net.ConnectException: Connection refused";

        var f1 = failure("com.example.FooTest", "testA", "context for [Config@aaa]", trace1, null);
        var f2 = failure("com.example.BarTest", "testB", "context for [Config@bbb]", trace2, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testMethod()).isEqualTo("testA, testB");
    }

    @Test
    void shouldSeparateFailuresWithDifferentRootCauses() {
        var f1 = failure("com.example.FooTest", "testA", "error", TRACE_WITH_ROOT_CAUSE_A, null);
        var f2 = failure("com.example.FooTest", "testB", "error", TRACE_WITH_ROOT_CAUSE_B, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldFallBackToFirstLineOfMessageWhenNoCausedBy() {
        var f1 = failure("com.example.FooTest", "testA", "expected:<200> but was:<404>", TRACE_NO_CAUSED_BY, null);
        var f2 = failure("com.example.FooTest", "testB", "expected:<200> but was:<404>", TRACE_NO_CAUSED_BY, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testMethod()).isEqualTo("testA, testB");
    }

    @Test
    void shouldFormatTestMethodSummaryForThreeOrFewerMethods() {
        var f1 = failure("com.example.FooTest", "testAlpha", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f2 = failure("com.example.FooTest", "testBeta", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testMethod()).isEqualTo("testAlpha, testBeta");
    }

    @Test
    void shouldFormatTestMethodSummaryWithMoreThanThreeMethods() {
        var f1 = failure("com.example.FooTest", "testA", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f2 = failure("com.example.FooTest", "testB", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f3 = failure("com.example.FooTest", "testC", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f4 = failure("com.example.FooTest", "testD", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f5 = failure("com.example.FooTest", "testE", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3, f4, f5));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testMethod()).isEqualTo("testA, testB, testC (+2 more)");
    }

    @Test
    void shouldConsolidateTestClassWhenAllSameClass() {
        var f1 = failure("com.example.BootstrapTest", "testA", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f2 = failure("com.example.BootstrapTest", "testB", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f3 = failure("com.example.BootstrapTest", "testC", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testClass()).isEqualTo("com.example.BootstrapTest");
    }

    @Test
    void shouldConsolidateTestClassWhenMultipleClasses() {
        var f1 = failure("com.example.FooTest", "testA", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f2 = failure("com.example.BarTest", "testB", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testClass()).isEqualTo("com.example.FooTest, com.example.BarTest");
    }

    @Test
    void shouldMergeTestOutputWithSeparator() {
        var f1 = failure("com.example.FooTest", "testA", "e", TRACE_WITH_ROOT_CAUSE_A, "output1");
        var f2 = failure("com.example.FooTest", "testB", "e", TRACE_WITH_ROOT_CAUSE_A, "output2");
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testOutput()).isEqualTo("output1\n---\noutput2");
    }

    @Test
    void shouldSkipNullTestOutputsWhenMerging() {
        var f1 = failure("com.example.FooTest", "testA", "e", TRACE_WITH_ROOT_CAUSE_A, "output1");
        var f2 = failure("com.example.FooTest", "testB", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f3 = failure("com.example.FooTest", "testC", "e", TRACE_WITH_ROOT_CAUSE_A, "output3");
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testOutput()).isEqualTo("output1\n---\noutput3");
    }

    @Test
    void shouldReturnNullTestOutputWhenAllNull() {
        var f1 = failure("com.example.FooTest", "testA", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        var f2 = failure("com.example.FooTest", "testB", "e", TRACE_WITH_ROOT_CAUSE_A, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testOutput()).isNull();
    }

    @Test
    void shouldPreserveInsertionOrder() {
        var f1 = failure("com.example.FooTest", "testA", "error A", TRACE_WITH_ROOT_CAUSE_A, null);
        var f2 = failure("com.example.FooTest", "testB", "error B", TRACE_WITH_ROOT_CAUSE_B, null);
        var f3 = failure("com.example.FooTest", "testC", "error A", TRACE_WITH_ROOT_CAUSE_A, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3));

        assertThat(result).hasSize(2);
        // Group A first (f1 appeared before f2)
        assertThat(result.get(0).testMethod()).contains("testA");
        assertThat(result.get(1).testMethod()).contains("testB");
    }

    @Test
    void shouldGroupByNullMessageAndStackTrace() {
        var f1 = failure("com.example.FooTest", "testA", null, null, null);
        var f2 = failure("com.example.FooTest", "testB", null, null, null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().message()).isNull();
        assertThat(result.getFirst().stackTrace()).isNull();
        assertThat(result.getFirst().testMethod()).isEqualTo("testA, testB");
    }

    @Test
    void extractRootCauseKey_lastCausedByLine() {
        String trace = "java.lang.Exception: top\n"
                + "Caused by: java.lang.RuntimeException: mid\n"
                + "Caused by: java.io.IOException: root";
        var f = failure("c", "m", "msg", trace, null);
        assertThat(TestFailureDeduplicator.extractRootCauseKey(f))
                .isEqualTo("Caused by: java.io.IOException: root");
    }

    @Test
    void extractRootCauseKey_fallsBackToFirstLineOfMessage() {
        var f = failure("c", "m", "first line\nsecond line", TRACE_NO_CAUSED_BY, null);
        assertThat(TestFailureDeduplicator.extractRootCauseKey(f))
                .isEqualTo("first line");
    }

    @Test
    void extractRootCauseKey_nullBoth() {
        var f = failure("c", "m", null, null, null);
        assertThat(TestFailureDeduplicator.extractRootCauseKey(f)).isEmpty();
    }
}
