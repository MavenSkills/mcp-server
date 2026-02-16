# Design: Markdown Output Format for Tool Responses

**Date:** 2026-02-16
**Status:** Approved

## Problem

Tool responses are currently serialized as JSON strings inside MCP `TextContent`.
LLMs consuming these responses must parse nested JSON-in-JSON, which:
- Wastes tokens on structural syntax (braces, quotes, repeated keys)
- Is less natural for LLMs to read than prose/Markdown
- Roughly 2x more tokens than equivalent Markdown representation

## Decision

Replace JSON serialization of `BuildResult` with Markdown formatting in all three
tools (`maven_clean`, `maven_compile`, `maven_test`).

## Scope

**Changes:**
- New class `MarkdownFormatter` — converts `BuildResult` to Markdown string
- `CleanTool`, `CompileTool`, `TestTool` — replace `objectMapper.writeValueAsString(buildResult)` with `MarkdownFormatter.format(buildResult, operation)`

**No changes:**
- MCP wire protocol (`CallToolResult` with `TextContent`)
- Tool input parameters
- Internal model classes (`BuildResult`, `CompilationError`, `TestFailure`, `TestSummary`)
- Parsers (`CompilationOutputParser`, `SurefireReportParser`)

## Format Specification

### General Template

```
{Operation} {STATUS} ({duration}s)[ — {detail}]
[errors/failures grouped by context]
[> note]
```

### Formatting Rules

1. **Duration** — always in seconds, 1 decimal place (ms / 1000.0)
2. **File paths in test failures** — shortened class names (no package prefix), LLM only needs class + line
3. **Empty lines** — only before `###` headers (required by Markdown syntax)
4. **Notes** — as blockquote `>`, always at the end
5. **Indentation** — 2 spaces for stack traces, test output, raw output
6. **No emoji** — plain text statuses (SUCCESS/FAILURE/TIMEOUT)

### Scenarios

#### 1. Clean SUCCESS
```
Clean SUCCESS (0.8s)
```

#### 2. Clean FAILURE
```
Clean FAILURE (1.2s)

  [ERROR] Failed to execute goal org.apache.maven.plugins...
  (tail of raw Maven output, indented 2 spaces)
```

#### 3. Compile SUCCESS (no warnings)
```
Compile SUCCESS (3.2s)
```

#### 4. Compile SUCCESS (with warnings)
```
Compile SUCCESS (3.2s) — 5 warnings
```
Only the warning count is shown, no details.

#### 5. Compile FAILURE
```
Compile FAILURE (2.3s) — 3 errors

### src/main/java/com/example/Foo.java
- L42:15 — cannot find symbol: variable bar
- L58 — incompatible types: String cannot be converted to int

### src/main/java/com/example/Baz.java
- L12:8 — package org.missing does not exist
```
Errors are grouped by file. Each error shows line[:column] and message.

#### 6. Test SUCCESS
```
Test SUCCESS (5.1s) — 42 run, 0 failed
```

#### 7. Test SUCCESS (with note)
```
Test SUCCESS (5.1s) — 42 run, 0 failed

> Ran in testOnly mode (surefire:test). Lifecycle phases were skipped. If tests fail unexpectedly, re-run with testOnly=false.
```

#### 8. Test FAILURE (test failures)
```
Test FAILURE (5.1s) — 42 run, 2 failed, 1 skipped

### FAILED: FooTest#shouldCalculateTotal
expected: <100> but was: <99>
  at FooTest.shouldCalculateTotal(FooTest.java:25)
  at service.Calculator.total(Calculator.java:18)
  Test output:
  DEBUG: Processing item id=5...
  WARN: Overflow detected

### FAILED: BarTest#shouldHandleNull
Expected not null
  at BarTest.shouldHandleNull(BarTest.java:33)

> Ran in testOnly mode. Stale sources detected — auto-recompiled.
```

Stack traces and test output are indented with 2 spaces (inline, no code blocks).

#### 9. Test FAILURE (compilation error, no XML reports)
Same format as Compile FAILURE — the tool already produces `CompilationError` in this case.

#### 10. Timeout (any tool)
```
Compile TIMEOUT (30.0s)
```

## API Design

```java
public class MarkdownFormatter {
    /**
     * Formats a BuildResult as a Markdown string.
     *
     * @param result    the build result to format
     * @param operation the operation name: "Clean", "Compile", or "Test"
     * @return Markdown-formatted string
     */
    public static String format(BuildResult result, String operation) { ... }
}
```

- Pure function, no side effects
- Easy to unit test with known inputs/outputs
- Delegates to private methods per section (header, errors, failures, note)

## Token Impact Estimate

| Scenario | JSON tokens (approx) | Markdown tokens (approx) | Savings |
|----------|----------------------|--------------------------|---------|
| Compile SUCCESS | ~30 | ~15 | ~50% |
| Compile FAILURE (3 errors) | ~280 | ~120 | ~57% |
| Test FAILURE (2 failures) | ~400+ | ~200 | ~50% |

## Migration

This is a breaking change for any tooling that parses the JSON output programmatically.
However, since the primary (and effectively only) consumers are LLMs via MCP,
this is acceptable. No backward-compatibility mechanism is needed.
