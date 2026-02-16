## MODIFIED Requirements

### Requirement: Test-only execution mode
The `maven_test` tool SHALL accept a `testOnly` boolean parameter (default: `true`).
- When `testOnly` is `false`, the tool SHALL execute Maven goal `test` (full lifecycle).
- When `testOnly` is `true`, the tool SHALL execute Maven goal `surefire:test` (direct plugin invocation, skipping all lifecycle phases), after performing stale-classes detection and auto-recompile if needed.

The `testOnly` parameter description in `INPUT_SCHEMA` SHALL include proactive guidance for LLM callers about when to use `testOnly=false` — specifically when changes go beyond Java source code (build config, generated source templates, new dependencies, resource files).

All other tool behavior (Surefire XML parsing, stack trace filtering, deduplication, output formatting) SHALL remain identical regardless of the `testOnly` value.

When Surefire XML reports are successfully parsed (i.e. `SurefireReportParser.parse()` returns a non-empty result), the `output` field in `BuildResult` SHALL be null, regardless of whether the build succeeded or failed. The structured `failures` and `summary` fields contain all information the agent needs.

When Surefire XML reports are NOT available (e.g. compilation failure during test phase), the `output` field SHALL contain the tail of raw Maven stdout (via `ToolUtils.tailLines()` with `DEFAULT_OUTPUT_TAIL_LINES`), not the full stdout.

#### Scenario: Default behavior is testOnly
- **WHEN** `maven_test` is called without `testOnly` parameter
- **THEN** the tool SHALL execute in testOnly mode (goal `surefire:test`), including stale detection and auto-recompile

#### Scenario: Full lifecycle explicit
- **WHEN** `maven_test` is called with `testOnly: false`
- **THEN** the tool SHALL execute goal `test` (full Maven lifecycle)

#### Scenario: Test-only mode activated
- **WHEN** `maven_test` is called with `testOnly: true` and `target/test-classes` exists
- **THEN** the tool SHALL execute goal `surefire:test` (direct plugin invocation)

#### Scenario: Test-only mode with test filter
- **WHEN** `maven_test` is called with `testOnly: true` and `testFilter: "MyTest"`
- **THEN** the tool SHALL execute `surefire:test` with `-Dtest=MyTest -DfailIfNoTests=false` arguments

#### Scenario: Output null when Surefire XML available and tests fail
- **WHEN** `maven_test` runs and Surefire XML reports are parsed successfully and tests fail
- **THEN** the `output` field in `BuildResult` SHALL be null
- **AND** the `failures` and `summary` fields SHALL contain the structured test results

#### Scenario: Output null when Surefire XML available and tests pass
- **WHEN** `maven_test` runs and Surefire XML reports are parsed successfully and all tests pass
- **THEN** the `output` field SHALL be null

#### Scenario: Output tail when no Surefire XML (compilation error)
- **WHEN** `maven_test` runs and no Surefire XML reports are found (e.g. compilation failure)
- **THEN** the `output` field SHALL contain the last 50 lines of Maven stdout
- **AND** the `errors` field SHALL contain structured compilation errors

#### Scenario: Auto-recompile failure uses tail output
- **WHEN** `testOnly` is `true` and auto-recompile fails
- **THEN** the `output` field SHALL contain the last 50 lines of the recompile Maven stdout

### Requirement: maven_test response format
The `maven_test` tool SHALL return a `CallToolResult` containing a single `TextContent` with a Markdown-formatted string produced by `MarkdownFormatter.format(buildResult, "Test")`. The response is plain text optimized for LLM consumption.

#### Scenario: Test success
- **WHEN** all tests pass with 42 run, 0 failed
- **THEN** the tool SHALL return a Markdown string: `Test SUCCESS ({duration}s) — 42 run, 0 failed`

#### Scenario: Test failure with structured failures
- **WHEN** tests fail and Surefire XML reports are parsed
- **THEN** the tool SHALL return a Markdown string starting with `Test FAILURE ({duration}s) — N run, M failed` followed by `### FAILED:` sections for each failure

#### Scenario: Test failure with compilation error
- **WHEN** test execution fails due to compilation error (no Surefire XML)
- **THEN** the tool SHALL return a Markdown string with compilation errors grouped by file, same as compile tool failure format

#### Scenario: Auto-recompile failure
- **WHEN** `testOnly` is `true` and auto-recompile fails
- **THEN** the tool SHALL return a Markdown-formatted failure with the recompile error details
