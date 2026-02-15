## Why

The `BuildResult` response from `maven_test` wastes agent tokens in three places. Real case: 205 errored tests (Redis container port conflict) → 5.4 MB response:
- **`output`** (5.1 MB) — raw Maven stdout with no size limit, full of repeated stack traces and framework logs
- **`message` and segment headers in failures** (168 KB) — Spring `WebMergedContextConfiguration.toString()` repeated twice per failure (once in `message`, once as first line of `stackTrace`); ~3.8 KB noise × 2 per failure
- **`failures` deduplication** — 22 failures with identical root cause (`address already in use`) treated as unique because `message` differs by object hash

## What Changes

- **Drop `output` when Surefire XML is available** — structured `failures[]` and `summary` already contain everything the agent needs; raw output is redundant
- **Tail ~50 lines of `output` when Surefire XML unavailable** (fallback for compilation/infrastructure errors) — in Maven output root cause is always at the end; simple, predictable token size
- **Truncate segment headers in `StackTraceProcessor`** — exception headers like `java.lang.IllegalStateException: Failed to load ApplicationContext for [WebMergedContextConfiguration@... 3800 chars]` truncated to ~200 chars. Covers both `message` field (same source) and first line of `stackTrace`
- **Truncate `message` in `SurefireReportParser`** — same truncation at parse time, so `message` field is compact regardless of stack trace processing
- **Deduplicate failures by root cause** — compare last `Caused by:` line instead of full message (which differs only by object hashes)
- **Remove `MavenOutputFilter`** — `inFailureBlock` flag never resets; tail makes the filter redundant

## Capabilities

### New Capabilities

- `output-size-guard`: Limit `output` field size in `BuildResult` — null when structured data suffices, tail last N lines as fallback

### Modified Capabilities

- `test-tool`: Change output inclusion logic — null when Surefire XML parsed successfully
- `test-failure-dedup`: Change dedup key from full message to root cause
- `compilation-parsing`: Tail last N lines instead of full output on compilation errors
- `suppressed-exception-parsing`: Truncate segment headers (exception message lines) to ~200 chars in `StackTraceProcessor`

## Impact

- `MavenOutputFilter` — remove (replaced by tail)
- `StackTraceProcessor` — truncate segment headers to ~200 chars
- `SurefireReportParser` — truncate `message` field at parse time
- `TestTool.create()` — change output assignment condition
- `CompileTool` / `CleanTool` — tail instead of raw stdout
- `ToolUtils` — new `tailLines()` helper
- `TestFailureDeduplicator` — change dedup key to root cause
- No API changes (fields were always optional/nullable, only value sizes change)
