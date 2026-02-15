## Context

The `maven_test` tool pipeline is:

```
SurefireReportParser.parse() → List<TestFailure>
  → TestTool.processStackTraces() → List<TestFailure>
    → new BuildResult(..., processedFailures, ...)
      → objectMapper.writeValueAsString(buildResult)
```

When many tests fail from the same root cause (e.g., compilation error), the list contains N entries with identical `(message, stackTrace)` — each serialized fully, wasting tokens. There is currently no grouping step.

`TestFailure` is a record: `(testClass, testMethod, message, stackTrace, testOutput)`. `BuildResult.failures` is `List<TestFailure>`. Both use `@JsonInclude(NON_NULL)`.

## Goals / Non-Goals

**Goals:**

- Reduce token output when multiple test failures share the same root cause
- Keep the change minimal — one new utility class, one call site change
- Maintain full backward compatibility of the JSON schema

**Non-Goals:**

- Configurable dedup behavior (on/off toggle, threshold parameter) — not needed until proven otherwise
- Deduplication of compilation errors (`List<CompilationError>`) — different structure, separate concern
- Heuristic/fuzzy matching (e.g., "similar" stack traces) — exact equality is sufficient and safe

## Decisions

### 1. New `TestFailureDeduplicator` class in `parser` package

**Decision:** Create `io.github.mavenmcp.parser.TestFailureDeduplicator` with a single static method `deduplicate(List<TestFailure>)`.

**Rationale:** Follows the existing pattern — `StackTraceProcessor` is a stateless utility in `parser` with a static `process()` method. Keeping dedup logic out of `TestTool` maintains single-responsibility. A dedicated class is easy to unit-test in isolation.

**Alternative considered:** Private method in `TestTool`. Rejected because `TestTool` is already 215 lines and its tests (`TestToolTest`) use an integration-style approach (MCP exchange). A separate class allows pure unit tests.

### 2. Grouping key as a private record

**Decision:** Use a private record `GroupKey(String message, String stackTrace)` inside `TestFailureDeduplicator`. Java records provide correct `equals()`/`hashCode()` for free, including null handling.

**Rationale:** No external dependency. Records treat `null` fields as equal in `equals()`, which matches the spec requirement. No need for a separate key class file — it's an implementation detail.

**Alternative considered:** `Map.Entry<String, String>` or manual key string (`message + "||" + stackTrace`). Rejected — record is cleaner, no collision risk, no allocation of concatenated strings.

### 3. `LinkedHashMap` for insertion-order grouping

**Decision:** Use `LinkedHashMap<GroupKey, List<TestFailure>>` to group failures while preserving first-occurrence order.

**Rationale:** Spec requires groups to appear in the order of their first failure. `LinkedHashMap` guarantees insertion order. The list size is bounded by test count (typically <1000), so performance is not a concern.

### 4. Summary format with N=3 threshold

**Decision:** For grouped entries, consolidate `testMethod` and `testClass` using format: first 3 items joined by `, `, then ` (+M more)` if more exist. The threshold N=3 is a constant in the class.

**Rationale:** Balances informativeness (agent sees a few method names) with brevity. N=3 keeps the field short even for large groups. Making it a named constant allows easy adjustment later without changing the API.

### 5. Integration point: after `processStackTraces()`, before `BuildResult`

**Decision:** In `TestTool.create()`, add one line after the `processStackTraces()` call:

```java
var processedFailures = processStackTraces(sr.failures(), appPackage, stackTraceLines);
var deduplicatedFailures = TestFailureDeduplicator.deduplicate(processedFailures);
// use deduplicatedFailures in BuildResult constructor
```

**Rationale:** Dedup must run after stack trace processing because `processStackTraces()` modifies the `stackTrace` field (truncation, filtering). Grouping on pre-processed traces would yield different keys than the final output. Single call site, minimal diff.

### 6. `testOutput` merging strategy

**Decision:** Concatenate non-null `testOutput` values with `"\n---\n"` separator. If all null, result is null.

**Rationale:** Test output is free-form text. A visible separator helps the agent distinguish outputs from different tests. Null preservation avoids empty strings in JSON (thanks to `@JsonInclude(NON_NULL)`).

## Risks / Trade-offs

- **[Under-grouping after stack trace processing]** → If `StackTraceProcessor` produces slightly different output for identical input (e.g., non-deterministic frame ordering), failures that should group won't. Mitigation: `StackTraceProcessor` is deterministic (verified by reading source). Risk is theoretical only.

- **[`testMethod` field semantics change]** → Consumers that parse `testMethod` as a single method name will see a comma-separated summary. Mitigation: The MCP protocol treats this as display text for LLM agents, not a structured identifier. No known consumer parses it programmatically.

- **[Large test output concatenation]** → If 100 tests each have 2KB output, the merged `testOutput` could be 200KB. Mitigation: `testOutputLimit` parameter already caps per-test output upstream in `SurefireReportParser`. In practice, test outputs for the same root cause are similar or empty.
