## ADDED Requirements

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
