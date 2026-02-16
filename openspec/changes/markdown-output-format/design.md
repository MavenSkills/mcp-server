## Context

All three MCP tools (`maven_clean`, `maven_compile`, `maven_test`) currently serialize `BuildResult` to JSON via Jackson `ObjectMapper` and return it as `TextContent`. The internal pipeline — `MavenRunner` → parsers → `BuildResult` model — is well-tested and stable. The only change point is the final serialization step in each tool's `create()` method.

The existing design doc at `docs/plans/2026-02-16-markdown-output-design.md` contains the approved format specification with concrete examples for all 10 scenarios.

## Goals / Non-Goals

**Goals:**
- Replace JSON serialization with Markdown formatting in tool responses
- Reduce token usage by ~50% for LLM consumers
- Keep the formatter as a pure, stateless function with no external dependencies
- Maintain full information fidelity — every field in `BuildResult` maps to a Markdown section

**Non-Goals:**
- Changing the MCP wire protocol or `CallToolResult` structure
- Modifying `BuildResult`, `CompilationError`, `TestFailure`, `TestSummary` models
- Modifying parsers (`CompilationOutputParser`, `SurefireReportParser`)
- Adding configurable output format (JSON vs Markdown toggle) — Markdown only
- Removing Jackson from the project (still used for JSON-RPC, tool input deserialization, etc.)

## Decisions

### D1: Single static formatter class

**Decision:** One `MarkdownFormatter` class with a single public entry point `format(BuildResult, String)`.

**Rationale:** The formatter needs no state, no configuration, and no DI. A static method keeps it simple and directly testable. Private helper methods (`appendHeader`, `appendErrors`, `appendFailures`, `appendRawOutput`, `appendNote`) decompose the logic by section.

**Alternative considered:** Per-tool formatters or a strategy pattern — rejected because all three tools share the same `BuildResult` model and the output differs only by operation name.

### D2: Operation name passed as string parameter

**Decision:** The caller passes `"Clean"`, `"Compile"`, or `"Test"` as the operation name.

**Rationale:** Avoids coupling the formatter to tool classes or introducing an enum for three values. The operation name appears only in the first line of output.

### D3: Duration formatting — milliseconds to seconds with 1 decimal

**Decision:** `duration / 1000.0` formatted as `"%.1fs"` (e.g., `800` → `"0.8s"`).

**Rationale:** Seconds are more human/LLM-readable than milliseconds. One decimal place provides enough precision without noise.

### D4: Header detail line — priority-based suffix

**Decision:** The header line includes at most one detail suffix, chosen by priority:
1. Test summary (`N run, M failed[, K skipped]`) — when `summary` is present
2. Error count (`N errors`) — when `errors` is non-empty
3. Warning count (`N warnings`) — when `warnings` is non-empty

**Rationale:** Avoids cluttered headers. Test results are the most informative; error/warning counts are secondary.

### D5: Compilation errors grouped by file

**Decision:** Errors are grouped by file path using `LinkedHashMap` to preserve encounter order. Each group has a `### file` header with `- L{line}[:{col}] — {message}` items.

**Rationale:** Grouping by file matches how developers and LLMs reason about fixes. Preserving order keeps output deterministic.

### D6: Test failure class names shortened

**Decision:** `shortClassName()` strips the package prefix from `testClass` (e.g., `com.example.FooTest` → `FooTest`).

**Rationale:** Package names waste tokens and add no useful context for identifying which test failed.

### D7: Stack traces and test output indented with 2 spaces

**Decision:** Stack trace lines and test output lines are prefixed with 2 spaces (not fenced code blocks).

**Rationale:** Code blocks add triple-backtick overhead. Two-space indentation is compact and readable.

### D8: Raw output for failure without structured errors

**Decision:** When a tool fails but produces no structured errors (e.g., clean failure), the raw Maven output is included with 2-space indentation.

**Rationale:** The LLM needs something to diagnose the failure. Raw output is the fallback.

### D9: Notes as blockquote at the end

**Decision:** The `note` field (e.g., testOnly mode notice) renders as `> {note}` at the bottom, separated by a blank line.

**Rationale:** Blockquotes are semantically appropriate for advisory information and visually distinct from error content.

## Risks / Trade-offs

- **Breaking change for JSON consumers** → Acceptable: only LLMs consume tool output via MCP. No programmatic JSON parsing exists outside tests.
- **Format not machine-parseable** → Mitigated: `BuildResult` model and its JSON serialization remain intact for any future use. Only the MCP response layer changes.
- **Singular/plural grammar (1 error vs 2 errors)** → Low risk, handled with ternary in formatter. Tested explicitly.
- **Large stack traces could produce long output** → Existing `stackTraceLines` limit in `TestTool` caps traces before they reach `BuildResult`. Formatter does not add its own truncation.
