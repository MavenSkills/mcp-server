## Context

The `maven_test` response wastes agent tokens in three places. Real case (205 errored tests, Redis port conflict) → 5.4 MB response:

1. **`output`** (5.1 MB) — raw Maven stdout with no size limit. `MavenOutputFilter` has a bug (`inFailureBlock` never resets). `CompileTool` and `CleanTool` don't filter at all.
2. **`message` + `stackTrace` headers in failures** (168 KB / 22 failures) — Spring `WebMergedContextConfiguration.toString()` = ~3.8 KB noise appearing **twice** per failure: once in `message` field (from XML `message` attr), once as first line of `stackTrace` (from XML text content). `StackTraceProcessor` already does smart Caused-by + app-frame filtering but unconditionally preserves full segment headers.
3. **Failure deduplication** — 22 failures with identical root cause (`address already in use`) not deduplicated because `message` differs by object hash (`@4d45b457` vs `@42ba74f9`).

## Goals / Non-Goals

**Goals:**
- Tool response stays within a few KB in all scenarios
- Null output when structured data (Surefire XML) is available
- Failure messages and stack trace headers contain only actionable info, not framework dumps
- Failures with identical root cause are collapsed
- Consistent approach across all tools

**Non-Goals:**
- Changing `BuildResult` format or field names (backward compatible)
- Intelligent summarization of content (overengineering)

## Decisions

### 1. Tail instead of filter

Replace `MavenOutputFilter.filter()` with simple `tailLines(output, N)`. Rationale:
- In Maven output, root cause is always at the end (BUILD FAILURE block, Caused by)
- Tail is simpler, predictable, and bug-free unlike the filter
- `MavenOutputFilter` becomes redundant — remove it

**Alternative considered:** fix `inFailureBlock` + add cap. Rejected — more complex, and tail yields better results (end of output is most valuable).

### 2. Null output when Surefire XML is available

In `TestTool`: when `SurefireReportParser.parse()` returns a result, set `output = null`. Structured `failures[]` + `summary` contain everything the agent needs. Raw output is redundant.

Applies to both paths: main (test execution) and auto-recompile (where compilation output also has structured `errors[]`).

### 3. DEFAULT_OUTPUT_TAIL_LINES = 50

Add `tailLines(String output, int lines)` method and constant to `ToolUtils`. 50 lines ≈ 2-4 KB, enough for BUILD FAILURE block with root cause.

Usage:
- `TestTool` — fallback when no Surefire XML
- `CompileTool` — instead of raw stdout (errors[] already has structured data)
- `CleanTool` — instead of raw stdout

### 4. Truncate segment headers in StackTraceProcessor

`StackTraceProcessor` already does Caused-by chain + app-frame filtering, but unconditionally includes full segment header lines. These headers contain the exception message which can be huge (Spring context toString = 3.8 KB).

Truncate each segment header to ~200 chars. This covers:
- Top-level exception: `java.lang.IllegalStateException: Failed to load ApplicationContext...` (not the 3.8 KB toString)
- Each `Caused by:` line: keep exception type + short message

Example before (3.9 KB):
```
java.lang.IllegalStateException: Failed to load ApplicationContext for [WebMergedContextConfiguration@4d45b457 testClass = ... contextCustomizers = [... 3800 chars ...]]
```

Example after (~100 chars):
```
java.lang.IllegalStateException: Failed to load ApplicationContext for [WebMergedContextConfiguration@4d45b457 testClass = com.devskiller.auth.company.CompanySetupServiceTest, ...
```

### 5. Truncate `message` in SurefireReportParser

Same truncation applied at parse time to the `message` field (sourced from XML `message` attribute). Truncate to first line or ~200 chars, whichever is shorter.

This ensures `message` is compact regardless of whether `StackTraceProcessor` runs (e.g. when `appPackage` is null and no frame filtering happens).

### 6. Deduplicate failures by root cause instead of full message

Current `TestFailureDeduplicator` uses full message as dedup key. Messages that differ only by object hash (`@4d45b457` vs `@42ba74f9`) are treated as unique.

Change dedup key to: last `Caused by:` line from stackTrace (the root cause). If no Caused by present, fall back to first line of message. This collapses 22 failures with identical root cause into 1 representative failure + count.

### 7. Remove MavenOutputFilter

Class becomes redundant after tail approach. Delete `MavenOutputFilter.java` and its tests.

## Risks / Trade-offs

**[Loss of context in middle of output]** → Root cause in Maven is always at the end. Structured data (errors[], failures[]) covers the rest. Minimal risk.

**[50 lines may not suffice for edge cases]** → Constant is easy to change. 50 lines covers BUILD FAILURE + Caused by chain + context.

**[Header truncation loses detail]** → The truncated part is Spring framework toString() noise (annotations, customizers, property sources). The actual exception type + class name are always in the first ~200 chars. Full Caused by chain is preserved.

**[Root-cause dedup may over-collapse]** → Different test classes could have the same root cause for different reasons. Mitigated by keeping a count of collapsed failures so the agent knows the scope.

**[Removing MavenOutputFilter]** → Internal class, no external consumers. Not breaking.
