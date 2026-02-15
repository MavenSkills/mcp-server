package io.github.mavenmcp.parser;

import io.github.mavenmcp.model.TestFailure;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFailureDeduplicatorTest {

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
        var f = failure("com.example.FooTest", "testA", "error", "trace", "output");
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isSameAs(f);
    }

    @Test
    void shouldPassThroughUniqueFailuresUnchanged() {
        var f1 = failure("com.example.FooTest", "testA", "error A", "trace A", null);
        var f2 = failure("com.example.BarTest", "testB", "error B", "trace B", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isSameAs(f1);
        assertThat(result.get(1)).isSameAs(f2);
    }

    @Test
    void shouldGroupIdenticalFailuresIntoOne() {
        var f1 = failure("com.example.FooTest", "testA", "error", "trace", null);
        var f2 = failure("com.example.FooTest", "testB", "error", "trace", null);
        var f3 = failure("com.example.FooTest", "testC", "error", "trace", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().message()).isEqualTo("error");
        assertThat(result.getFirst().stackTrace()).isEqualTo("trace");
        assertThat(result.getFirst().testMethod()).isEqualTo("testA, testB, testC");
    }

    @Test
    void shouldProduceSeparateEntriesForDifferentMessages() {
        var f1 = failure("com.example.FooTest", "testA", "error A", "trace", null);
        var f2 = failure("com.example.FooTest", "testB", "error B", "trace", null);
        var f3 = failure("com.example.FooTest", "testC", "error A", "trace", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).message()).isEqualTo("error A");
        assertThat(result.get(0).testMethod()).isEqualTo("testA, testC");
        assertThat(result.get(1).message()).isEqualTo("error B");
        assertThat(result.get(1).testMethod()).isEqualTo("testB");
    }

    @Test
    void shouldProduceSeparateEntriesForDifferentStackTraces() {
        var f1 = failure("com.example.FooTest", "testA", "error", "trace 1", null);
        var f2 = failure("com.example.FooTest", "testB", "error", "trace 2", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldFormatTestMethodSummaryForThreeOrFewerMethods() {
        var f1 = failure("com.example.FooTest", "testAlpha", "e", "t", null);
        var f2 = failure("com.example.FooTest", "testBeta", "e", "t", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testMethod()).isEqualTo("testAlpha, testBeta");
    }

    @Test
    void shouldFormatTestMethodSummaryWithMoreThanThreeMethods() {
        var f1 = failure("com.example.FooTest", "testA", "e", "t", null);
        var f2 = failure("com.example.FooTest", "testB", "e", "t", null);
        var f3 = failure("com.example.FooTest", "testC", "e", "t", null);
        var f4 = failure("com.example.FooTest", "testD", "e", "t", null);
        var f5 = failure("com.example.FooTest", "testE", "e", "t", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3, f4, f5));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testMethod()).isEqualTo("testA, testB, testC (+2 more)");
    }

    @Test
    void shouldConsolidateTestClassWhenAllSameClass() {
        var f1 = failure("com.example.BootstrapTest", "testA", "e", "t", null);
        var f2 = failure("com.example.BootstrapTest", "testB", "e", "t", null);
        var f3 = failure("com.example.BootstrapTest", "testC", "e", "t", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testClass()).isEqualTo("com.example.BootstrapTest");
    }

    @Test
    void shouldConsolidateTestClassWhenMultipleClasses() {
        var f1 = failure("com.example.FooTest", "testA", "e", "t", null);
        var f2 = failure("com.example.BarTest", "testB", "e", "t", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testClass()).isEqualTo("com.example.FooTest, com.example.BarTest");
    }

    @Test
    void shouldMergeTestOutputWithSeparator() {
        var f1 = failure("com.example.FooTest", "testA", "e", "t", "output1");
        var f2 = failure("com.example.FooTest", "testB", "e", "t", "output2");
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testOutput()).isEqualTo("output1\n---\noutput2");
    }

    @Test
    void shouldSkipNullTestOutputsWhenMerging() {
        var f1 = failure("com.example.FooTest", "testA", "e", "t", "output1");
        var f2 = failure("com.example.FooTest", "testB", "e", "t", null);
        var f3 = failure("com.example.FooTest", "testC", "e", "t", "output3");
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testOutput()).isEqualTo("output1\n---\noutput3");
    }

    @Test
    void shouldReturnNullTestOutputWhenAllNull() {
        var f1 = failure("com.example.FooTest", "testA", "e", "t", null);
        var f2 = failure("com.example.FooTest", "testB", "e", "t", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().testOutput()).isNull();
    }

    @Test
    void shouldPreserveInsertionOrder() {
        var f1 = failure("com.example.FooTest", "testA", "error A", "trace A", null);
        var f2 = failure("com.example.FooTest", "testB", "error B", "trace B", null);
        var f3 = failure("com.example.FooTest", "testC", "error A", "trace A", null);
        var f4 = failure("com.example.FooTest", "testD", "error C", "trace C", null);
        List<TestFailure> result = TestFailureDeduplicator.deduplicate(List.of(f1, f2, f3, f4));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).message()).isEqualTo("error A");
        assertThat(result.get(1).message()).isEqualTo("error B");
        assertThat(result.get(2).message()).isEqualTo("error C");
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
}
