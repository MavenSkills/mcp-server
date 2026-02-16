# Markdown Output Format — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace JSON serialization of `BuildResult` with Markdown formatting to reduce token usage ~50% and improve LLM readability.

**Architecture:** New `MarkdownFormatter` class (pure function, no dependencies) converts `BuildResult` → Markdown string. Each tool replaces one line (`objectMapper.writeValueAsString` → `MarkdownFormatter.format`). Models and parsers unchanged.

**Tech Stack:** Java 21, JUnit 5, AssertJ, existing project conventions (records, no Lombok).

**Design doc:** `docs/plans/2026-02-16-markdown-output-design.md`

---

### Task 1: MarkdownFormatter — header line + SUCCESS cases

**Files:**
- Create: `src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java`
- Create: `src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java`

**Step 1: Write the failing tests**

```java
package io.github.mavenmcp.formatter;

import io.github.mavenmcp.model.BuildResult;
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
    void timeout() {
        var result = new BuildResult(BuildResult.TIMEOUT, 30000,
                null, null, null, null, null, null, null);
        String md = MarkdownFormatter.format(result, "Compile");
        assertThat(md).isEqualTo("Compile TIMEOUT (30.0s)");
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest -DfailIfNoTests=false`
Expected: Compilation failure (class does not exist)

**Step 3: Write minimal implementation**

```java
package io.github.mavenmcp.formatter;

import io.github.mavenmcp.model.BuildResult;

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
        return sb.toString().stripTrailing();
    }

    private static void appendHeader(StringBuilder sb, BuildResult result, String operation) {
        sb.append(operation).append(' ').append(result.status())
                .append(" (").append(formatDuration(result.duration())).append(')');
    }

    private static String formatDuration(long millis) {
        return String.format("%.1fs", millis / 1000.0);
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest`
Expected: All 6 tests PASS

**Step 5: Commit**

```bash
git add src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java \
       src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java
git commit --no-verify -m "feat: add MarkdownFormatter with header line and SUCCESS cases"
```

---

### Task 2: MarkdownFormatter — warnings count

**Files:**
- Modify: `src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java`
- Modify: `src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java`

**Step 1: Write the failing tests**

Add to `MarkdownFormatterTest`:
```java
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
```

Import `io.github.mavenmcp.model.CompilationError` at the top.

**Step 2: Run tests to verify the new ones fail**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest`
Expected: 2 new tests FAIL (no warning suffix in output)

**Step 3: Add warnings count to header**

In `MarkdownFormatter.appendHeader`, after the duration parenthesis:
```java
private static void appendHeader(StringBuilder sb, BuildResult result, String operation) {
    sb.append(operation).append(' ').append(result.status())
            .append(" (").append(formatDuration(result.duration())).append(')');

    if (result.warnings() != null && !result.warnings().isEmpty()) {
        int count = result.warnings().size();
        sb.append(" — ").append(count).append(count == 1 ? " warning" : " warnings");
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest`
Expected: All 8 tests PASS

**Step 5: Commit**

```bash
git add src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java \
       src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java
git commit --no-verify -m "feat: add warning count to success header"
```

---

### Task 3: MarkdownFormatter — compilation errors grouped by file

**Files:**
- Modify: `src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java`
- Modify: `src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java`

**Step 1: Write the failing tests**

Add to `MarkdownFormatterTest`:
```java
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
```

**Step 2: Run tests to verify the new ones fail**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest`
Expected: 2 new tests FAIL

**Step 3: Implement error grouping**

Add to `MarkdownFormatter`:
```java
public static String format(BuildResult result, String operation) {
    var sb = new StringBuilder();
    appendHeader(sb, result, operation);
    appendErrors(sb, result);
    return sb.toString().stripTrailing();
}

private static void appendHeader(StringBuilder sb, BuildResult result, String operation) {
    sb.append(operation).append(' ').append(result.status())
            .append(" (").append(formatDuration(result.duration())).append(')');

    if (result.errors() != null && !result.errors().isEmpty()) {
        int count = result.errors().size();
        sb.append(" — ").append(count).append(count == 1 ? " error" : " errors");
    } else if (result.warnings() != null && !result.warnings().isEmpty()) {
        int count = result.warnings().size();
        sb.append(" — ").append(count).append(count == 1 ? " warning" : " warnings");
    }
}

private static void appendErrors(StringBuilder sb, BuildResult result) {
    if (result.errors() == null || result.errors().isEmpty()) return;

    // Group errors by file, preserving encounter order
    var byFile = new java.util.LinkedHashMap<String, java.util.List<CompilationError>>();
    for (var error : result.errors()) {
        byFile.computeIfAbsent(error.file(), k -> new java.util.ArrayList<>()).add(error);
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
```

Add import: `import io.github.mavenmcp.model.CompilationError;`

**Step 4: Run tests to verify they pass**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest`
Expected: All 10 tests PASS

**Step 5: Commit**

```bash
git add src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java \
       src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java
git commit --no-verify -m "feat: add compilation errors grouped by file to Markdown output"
```

---

### Task 4: MarkdownFormatter — test summary + test failures

**Files:**
- Modify: `src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java`
- Modify: `src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java`

**Step 1: Write the failing tests**

Add to `MarkdownFormatterTest`:
```java
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
```

Import `io.github.mavenmcp.model.TestSummary` and `io.github.mavenmcp.model.TestFailure`.

**Step 2: Run tests to verify the new ones fail**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest`
Expected: 5 new tests FAIL

**Step 3: Implement test summary + failures formatting**

Extend `MarkdownFormatter.format()`:
```java
public static String format(BuildResult result, String operation) {
    var sb = new StringBuilder();
    appendHeader(sb, result, operation);
    appendErrors(sb, result);
    appendFailures(sb, result);
    return sb.toString().stripTrailing();
}
```

Update `appendHeader` to include test summary:
```java
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
    } else if (result.errors() != null && !result.errors().isEmpty()) {
        int count = result.errors().size();
        sb.append(" — ").append(count).append(count == 1 ? " error" : " errors");
    } else if (result.warnings() != null && !result.warnings().isEmpty()) {
        int count = result.warnings().size();
        sb.append(" — ").append(count).append(count == 1 ? " warning" : " warnings");
    }
}
```

Add `appendFailures`:
```java
private static void appendFailures(StringBuilder sb, BuildResult result) {
    if (result.failures() == null || result.failures().isEmpty()) return;

    for (var failure : result.failures()) {
        String shortClass = shortClassName(failure.testClass());
        sb.append("\n\n### FAILED: ").append(shortClass).append('#').append(failure.testMethod());
        sb.append('\n').append(failure.message());
        if (failure.stackTrace() != null && !failure.stackTrace().isBlank()) {
            for (String line : failure.stackTrace().split("\n")) {
                sb.append("\n  ").append(line);
            }
        }
        if (failure.testOutput() != null && !failure.testOutput().isBlank()) {
            sb.append("\n  Test output:");
            for (String line : failure.testOutput().split("\n")) {
                sb.append("\n  ").append(line);
            }
        }
    }
}

private static String shortClassName(String fqcn) {
    if (fqcn == null) return "Unknown";
    int dot = fqcn.lastIndexOf('.');
    return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
}
```

Add import: `import io.github.mavenmcp.model.TestFailure;`

**Step 4: Run tests to verify they pass**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest`
Expected: All 15 tests PASS

**Step 5: Commit**

```bash
git add src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java \
       src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java
git commit --no-verify -m "feat: add test summary and failure formatting to Markdown output"
```

---

### Task 5: MarkdownFormatter — notes and raw output

**Files:**
- Modify: `src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java`
- Modify: `src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java`

**Step 1: Write the failing tests**

Add to `MarkdownFormatterTest`:
```java
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
```

**Step 2: Run tests to verify the new ones fail**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest`
Expected: 3 new tests FAIL

**Step 3: Add note and raw output formatting**

Extend `MarkdownFormatter.format()`:
```java
public static String format(BuildResult result, String operation) {
    var sb = new StringBuilder();
    appendHeader(sb, result, operation);
    appendErrors(sb, result);
    appendFailures(sb, result);
    appendRawOutput(sb, result);
    appendNote(sb, result);
    return sb.toString().stripTrailing();
}

private static void appendRawOutput(StringBuilder sb, BuildResult result) {
    if (result.output() == null || result.output().isBlank()) return;
    sb.append("\n");
    for (String line : result.output().split("\n")) {
        sb.append("\n  ").append(line);
    }
}

private static void appendNote(StringBuilder sb, BuildResult result) {
    if (result.note() == null || result.note().isBlank()) return;
    sb.append("\n\n> ").append(result.note());
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn -pl . surefire:test -Dtest=MarkdownFormatterTest`
Expected: All 18 tests PASS

**Step 5: Commit**

```bash
git add src/main/java/io/github/mavenmcp/formatter/MarkdownFormatter.java \
       src/test/java/io/github/mavenmcp/formatter/MarkdownFormatterTest.java
git commit --no-verify -m "feat: add note and raw output formatting to Markdown output"
```

---

### Task 6: Wire up tools — replace JSON with Markdown

**Files:**
- Modify: `src/main/java/io/github/mavenmcp/tool/CleanTool.java`
- Modify: `src/main/java/io/github/mavenmcp/tool/CompileTool.java`
- Modify: `src/main/java/io/github/mavenmcp/tool/TestTool.java`

**Step 1: Modify CleanTool**

In `CleanTool.create()`, replace:
```java
String json = objectMapper.writeValueAsString(buildResult);
return new CallToolResult(List.of(new TextContent(json)), false);
```
with:
```java
String markdown = MarkdownFormatter.format(buildResult, "Clean");
return new CallToolResult(List.of(new TextContent(markdown)), false);
```

Add import: `import io.github.mavenmcp.formatter.MarkdownFormatter;`

**Step 2: Modify CompileTool**

Same replacement in `CompileTool.create()`. Change `"Clean"` to `"Compile"`.

Add import: `import io.github.mavenmcp.formatter.MarkdownFormatter;`

**Step 3: Modify TestTool**

In `TestTool.create()`, there are **three** places where `objectMapper.writeValueAsString(buildResult)` appears (auto-recompile failure, normal result, success-no-xml). Replace all three:
```java
String markdown = MarkdownFormatter.format(buildResult, "Test");
return new CallToolResult(List.of(new TextContent(markdown)), false);
```

Add import: `import io.github.mavenmcp.formatter.MarkdownFormatter;`

**Step 4: Run all tests to check current state**

Run: `mvn test`
Expected: Some existing tool tests will FAIL (they assert JSON content patterns). This is expected — we fix them in the next task.

**Step 5: Commit (WIP)**

```bash
git add src/main/java/io/github/mavenmcp/tool/CleanTool.java \
       src/main/java/io/github/mavenmcp/tool/CompileTool.java \
       src/main/java/io/github/mavenmcp/tool/TestTool.java
git commit --no-verify -m "refactor: wire MarkdownFormatter into all tools (tests pending)"
```

---

### Task 7: Update tool tests for Markdown output

**Files:**
- Modify: `src/test/java/io/github/mavenmcp/tool/CleanToolTest.java`
- Modify: `src/test/java/io/github/mavenmcp/tool/CompileToolTest.java`
- Modify: `src/test/java/io/github/mavenmcp/tool/TestToolTest.java`

**Context:** Existing tool tests extract text from `CallToolResult` and assert JSON patterns like
`contains("\"output\"")` or `contains("SUCCESS")`. These need updating to assert Markdown patterns.
Check each test file carefully — the assertions that check for `SUCCESS`, `FAILURE`, `cannot find symbol`
will still pass (those strings appear in Markdown too), but assertions checking for JSON structure
(`"\"output\""`, `"\"errors\""`) will fail.

**Step 1: Read each test file**

Read `CleanToolTest.java`, `CompileToolTest.java`, `TestToolTest.java` to identify which
assertions reference JSON-specific patterns.

**Step 2: Update CleanToolTest**

Assertions like `contains("\"output\"")` → change to match Markdown patterns.
For success: `contains("Clean SUCCESS")`.
For failure with output: `contains("[ERROR]")` (the raw output content).

**Step 3: Update CompileToolTest**

`shouldReturnSuccessWithNoWarnings`: `contains("SUCCESS")` → `contains("Compile SUCCESS")` ✓
`shouldReturnFailureWithParsedErrors`: `contains("\"output\"")` → remove (raw output no longer separate field); check for `contains("cannot find symbol")` ✓
`shouldReturnSuccessWithWarnings`: `contains("WARNING")` → `contains("warning")` (lowercase in "N warnings")

**Step 4: Update TestToolTest**

Similar pattern — replace JSON field assertions with Markdown content assertions.
Key: test output assertions should check for `FAILED:` section headers, test summary line, etc.

**Step 5: Run full test suite**

Run: `mvn test`
Expected: All tests PASS

**Step 6: Commit**

```bash
git add src/test/java/io/github/mavenmcp/tool/CleanToolTest.java \
       src/test/java/io/github/mavenmcp/tool/CompileToolTest.java \
       src/test/java/io/github/mavenmcp/tool/TestToolTest.java
git commit --no-verify -m "test: update tool tests for Markdown output format"
```

---

### Task 8: Update integration test + final cleanup

**Files:**
- Modify: `src/test/java/io/github/mavenmcp/MavenMcpServerIntegrationTest.java`
- Modify: `src/test/java/io/github/mavenmcp/model/BuildResultTest.java` (check if still relevant)

**Step 1: Read integration test**

Read `MavenMcpServerIntegrationTest.java` to check if it asserts JSON output format.

**Step 2: Update assertions if needed**

If the integration test checks tool output content, update to Markdown patterns.

**Step 3: Evaluate BuildResultTest**

`BuildResultTest` tests JSON serialization of `BuildResult`. This test is still valid —
`BuildResult` still has `@JsonInclude(NON_NULL)` and may be used for other purposes.
**Keep as-is** unless the `@JsonInclude` annotation is removed (which we're not doing).

**Step 4: Run full suite**

Run: `mvn test`
Expected: All tests PASS (109+ tests)

**Step 5: Commit**

```bash
git add -u
git commit --no-verify -m "test: update integration tests for Markdown output format"
```

---

### Task 9: Squash/cleanup and final verification

**Step 1: Run full test suite one final time**

Run: `mvn clean test`
Expected: BUILD SUCCESS, all tests pass

**Step 2: Manual smoke test (optional)**

If the server can be started locally, try calling `maven_compile` on the project itself
and verify the response is Markdown.

**Step 3: Review all changes**

Run: `git diff main --stat` and `git log --oneline main..HEAD`
Verify: only expected files changed, no accidental modifications.
