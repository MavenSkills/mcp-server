package io.github.mavenmcp.formatter;

import io.github.mavenmcp.model.BuildResult;
import io.github.mavenmcp.model.CompilationError;
import io.github.mavenmcp.model.TestFailure;
import io.github.mavenmcp.model.TestSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownFormatterTest {

    @Test
    void cleanSuccess() {
        var result = new BuildResult(BuildResult.SUCCESS, 800,
                null, null, null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Clean");
        assertThat(md).isEqualTo("Clean SUCCESS (0.8s)");
    }

    @Test
    void compileSuccessNoWarnings() {
        var result = new BuildResult(BuildResult.SUCCESS, 3200,
                null, null, null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Compile");
        assertThat(md).isEqualTo("Compile SUCCESS (3.2s)");
    }

    @Test
    void compileSuccessEmptyErrorsAndWarnings() {
        var result = new BuildResult(BuildResult.SUCCESS, 3200,
                List.of(), List.of(), null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Compile");
        assertThat(md).isEqualTo("Compile SUCCESS (3.2s)");
    }

    @Test
    void durationFormattedToOneDecimal() {
        var result = new BuildResult(BuildResult.SUCCESS, 150,
                null, null, null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Clean");
        assertThat(md).isEqualTo("Clean SUCCESS (0.2s)");
    }

    @Test
    void durationZero() {
        var result = new BuildResult(BuildResult.SUCCESS, 0,
                null, null, null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Clean");
        assertThat(md).isEqualTo("Clean SUCCESS (0.0s)");
    }

    @Test
    void compileSuccessWithWarnings() {
        var w1 = new CompilationError("Foo.java", 10, null, "deprecated", "WARNING");
        var w2 = new CompilationError("Bar.java", 20, null, "unchecked", "WARNING");
        var result = new BuildResult(BuildResult.SUCCESS, 2000,
                null, List.of(w1, w2), null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Compile");
        assertThat(md).isEqualTo("Compile SUCCESS (2.0s) — 2 warnings");
    }

    @Test
    void compileSuccessWithOneWarning() {
        var w1 = new CompilationError("Foo.java", 10, null, "deprecated", "WARNING");
        var result = new BuildResult(BuildResult.SUCCESS, 1000,
                null, List.of(w1), null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Compile");
        assertThat(md).isEqualTo("Compile SUCCESS (1.0s) — 1 warning");
    }

    @Test
    void compileFailureWithErrors() {
        var e1 = new CompilationError("src/main/java/Foo.java", 42, 15, "cannot find symbol", "ERROR");
        var e2 = new CompilationError("src/main/java/Foo.java", 58, null, "incompatible types", "ERROR");
        var e3 = new CompilationError("src/main/java/Baz.java", 12, 8, "package does not exist", "ERROR");
        var result = new BuildResult(BuildResult.FAILURE, 2341,
                List.of(e1, e2, e3), null, null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Compile");
        assertThat(md).isEqualTo("""
                Compile FAILURE (2.3s) — 3 errors

                ### src/main/java/Foo.java
                - L42:15 — cannot find symbol
                - L58 — incompatible types

                ### src/main/java/Baz.java
                - L12:8 — package does not exist""");
    }

    @Test
    void compileFailureSingleError() {
        var e1 = new CompilationError("src/main/java/Foo.java", 10, null, "some error", "ERROR");
        var result = new BuildResult(BuildResult.FAILURE, 1000,
                List.of(e1), null, null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Compile");
        assertThat(md).isEqualTo("""
                Compile FAILURE (1.0s) — 1 error

                ### src/main/java/Foo.java
                - L10 — some error""");
    }

    @Test
    void testSuccess() {
        var summary = new TestSummary(42, 0, 0, 0);
        var result = new BuildResult(BuildResult.SUCCESS, 5100,
                null, null, summary, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Test");
        assertThat(md).isEqualTo("Test SUCCESS (5.1s) — 42 run, 0 failed");
    }

    @Test
    void testSuccessWithSkipped() {
        var summary = new TestSummary(42, 0, 3, 0);
        var result = new BuildResult(BuildResult.SUCCESS, 5100,
                null, null, summary, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Test");
        assertThat(md).isEqualTo("Test SUCCESS (5.1s) — 42 run, 0 failed, 3 skipped");
    }

    @Test
    void testFailureWithFailures() {
        var summary = new TestSummary(42, 2, 1, 0);
        var f1 = new TestFailure(
                "com.example.FooTest", "shouldCalc",
                "expected: <100> but was: <99>",
                "at FooTest.shouldCalc(FooTest.java:25)\nat Calculator.total(Calculator.java:18)",
                null);
        var f2 = new TestFailure(
                "com.example.BarTest", "shouldHandleNull",
                "Expected not null",
                "at BarTest.shouldHandleNull(BarTest.java:33)",
                null);
        var result = new BuildResult(BuildResult.FAILURE, 5100,
                null, null, summary, List.of(f1, f2), null, null, null);
        String md = MarkdownFormatter.format(result, "Test");
        assertThat(md).isEqualTo("""
                Test FAILURE (5.1s) — 42 run, 2 failed, 1 skipped

                ### FAILED: FooTest#shouldCalc
                expected: <100> but was: <99>
                  at FooTest.shouldCalc(FooTest.java:25)
                  at Calculator.total(Calculator.java:18)

                ### FAILED: BarTest#shouldHandleNull
                Expected not null
                  at BarTest.shouldHandleNull(BarTest.java:33)""");
    }

    @Test
    void testFailureWithTestOutput() {
        var summary = new TestSummary(1, 1, 0, 0);
        var f1 = new TestFailure(
                "com.example.FooTest", "shouldCalc",
                "assertion failed",
                "at FooTest.shouldCalc(FooTest.java:25)",
                "DEBUG: item=5\nWARN: overflow");
        var result = new BuildResult(BuildResult.FAILURE, 1000,
                null, null, summary, List.of(f1), null, null, null);
        String md = MarkdownFormatter.format(result, "Test");
        assertThat(md).isEqualTo("""
                Test FAILURE (1.0s) — 1 run, 1 failed

                ### FAILED: FooTest#shouldCalc
                assertion failed
                  at FooTest.shouldCalc(FooTest.java:25)
                  Test output:
                  DEBUG: item=5
                  WARN: overflow""");
    }

    @Test
    void testFailureNoStackTrace() {
        var summary = new TestSummary(1, 1, 0, 0);
        var f1 = new TestFailure(
                "com.example.FooTest", "shouldCalc",
                "assertion failed", null, null);
        var result = new BuildResult(BuildResult.FAILURE, 1000,
                null, null, summary, List.of(f1), null, null, null);
        String md = MarkdownFormatter.format(result, "Test");
        assertThat(md).isEqualTo("""
                Test FAILURE (1.0s) — 1 run, 1 failed

                ### FAILED: FooTest#shouldCalc
                assertion failed""");
    }

    @Test
    void testSuccessWithNote() {
        var summary = new TestSummary(42, 0, 0, 0);
        var result = new BuildResult(BuildResult.SUCCESS, 5100,
                null, null, summary, null, null, null,
                "Ran in testOnly mode. Skipped lifecycle phases.");
        String md = MarkdownFormatter.format(result, "Test");
        assertThat(md).isEqualTo("""
                Test SUCCESS (5.1s) — 42 run, 0 failed

                > Ran in testOnly mode. Skipped lifecycle phases.""");
    }

    @Test
    void cleanFailureWithRawOutput() {
        var result = new BuildResult(BuildResult.FAILURE, 1200,
                null, null, null, null, null,
                "[ERROR] Failed to execute goal\n[ERROR] BUILD FAILURE", null);
        String md = MarkdownFormatter.format(result, "Clean");
        assertThat(md).isEqualTo("""
                Clean FAILURE (1.2s)

                  [ERROR] Failed to execute goal
                  [ERROR] BUILD FAILURE""");
    }

    @Test
    void testFailureWithNoteAndFailures() {
        var summary = new TestSummary(1, 1, 0, 0);
        var f1 = new TestFailure("com.example.FooTest", "test1", "fail", null, null);
        var result = new BuildResult(BuildResult.FAILURE, 1000,
                null, null, summary, List.of(f1), null, null,
                "Stale sources detected.");
        String md = MarkdownFormatter.format(result, "Test");
        assertThat(md).isEqualTo("""
                Test FAILURE (1.0s) — 1 run, 1 failed

                ### FAILED: FooTest#test1
                fail

                > Stale sources detected.""");
    }

    @Test
    void timeout() {
        var result = new BuildResult(BuildResult.TIMEOUT, 30000,
                null, null, null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Compile");
        assertThat(md).isEqualTo("Compile TIMEOUT (30.0s)");
    }
}
