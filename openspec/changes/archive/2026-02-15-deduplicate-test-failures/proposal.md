## Why

When multiple tests fail for the same root cause (e.g., compilation error), `maven_test` returns identical `message` + `stackTrace` pairs repeated per test method. In real usage, 11 failures from the same `BootstrapTest` class produced ~11.5k tokens where ~90% was duplicated content. Claude Code itself warns about this (`Large MCP response (~11.5k tokens)`). At scale (100+ failures from one cause), this wastes tokens and fills the agent's context window with zero additional information.

## What Changes

- Add a deduplication step between `processStackTraces()` and JSON serialization in `TestTool`
- Group failures by `(message, stackTrace)` key
- For groups with >1 failure, emit a single `TestFailure` with a consolidated `testMethod` field (e.g., `"method1, method2 (+9 more)"`) and the stack trace included once
- Singleton groups pass through unchanged

## Capabilities

### New Capabilities

- `test-failure-dedup`: Deduplication logic that groups identical test failures and emits a compact representation, reducing token usage for repeated errors

### Modified Capabilities

_(none — `BuildResult` schema and `TestFailure` record stay unchanged; the dedup step operates on the existing `List<TestFailure>` before serialization)_

## Impact

- **Code:** `TestTool.java` — new dedup step inserted after `processStackTraces()` call (line ~119). New utility class or private method for grouping logic.
- **API:** No schema change. `BuildResult.failures` remains `List<TestFailure>`. The `testMethod` field carries a summary string instead of a single method name for grouped failures — agents already treat it as display text.
- **Dependencies:** None.
- **Risk:** Low. The grouping is a pure post-processing step on an in-memory list. Worst case: a subtle key mismatch causes under-grouping (safe — just less compression). Over-grouping is prevented by using exact `(message, stackTrace)` equality.
