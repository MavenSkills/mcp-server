## 1. MarkdownFormatter — header line and SUCCESS cases

- [x] 1.1 Create `MarkdownFormatterTest` with tests for: clean success, compile success (no warnings), compile success (empty lists), duration formatting (sub-second, zero), timeout
- [x] 1.2 Create `MarkdownFormatter` with `format(BuildResult, String)`, `appendHeader()`, and `formatDuration()` — pass all header/SUCCESS tests

## 2. MarkdownFormatter — warning count

- [x] 2.1 Add tests for warning count suffix: multiple warnings ("2 warnings"), single warning ("1 warning")
- [x] 2.2 Extend `appendHeader()` to append warning count when warnings present and no errors/summary

## 3. MarkdownFormatter — compilation errors grouped by file

- [x] 3.1 Add tests for compile failure: multiple errors across files (grouped by file, L{line}:{col} format), single error, optional column
- [x] 3.2 Implement `appendErrors()` — group by file with LinkedHashMap, render `### file` headers and `- L{line}[:{col}] — {message}` items; update header to show error count

## 4. MarkdownFormatter — test summary and test failures

- [x] 4.1 Add tests for: test success (summary in header), test success with skipped, test failure with stack traces, test failure with test output, test failure without stack trace
- [x] 4.2 Implement `appendFailures()` with `shortClassName()`, stack trace indentation (2 spaces), test output section; update `appendHeader()` for test summary with priority over errors/warnings

## 5. MarkdownFormatter — notes and raw output

- [x] 5.1 Add tests for: success with note (blockquote), clean failure with raw output (2-space indent), failure with note and failures (note at end)
- [x] 5.2 Implement `appendRawOutput()` (2-space indented lines) and `appendNote()` (blockquote `> {note}`)

## 6. Wire up tools — replace JSON with Markdown

- [x] 6.1 Replace `objectMapper.writeValueAsString(buildResult)` with `MarkdownFormatter.format(buildResult, "Clean")` in `CleanTool`
- [x] 6.2 Replace `objectMapper.writeValueAsString(buildResult)` with `MarkdownFormatter.format(buildResult, "Compile")` in `CompileTool`
- [x] 6.3 Replace all `objectMapper.writeValueAsString(buildResult)` calls with `MarkdownFormatter.format(buildResult, "Test")` in `TestTool` (3 call sites)

## 7. Update tool tests for Markdown output

- [x] 7.1 Update `CleanToolTest` assertions from JSON patterns to Markdown patterns (`"Clean SUCCESS"`, `"Clean FAILURE"`)
- [x] 7.2 Update `CompileToolTest` assertions from JSON field checks to Markdown content checks (`"Compile SUCCESS"`, `"Compile FAILURE"`, error messages)
- [x] 7.3 Update `TestToolTest` assertions from JSON patterns to Markdown patterns (`"Test SUCCESS"`, `"FAILED:"`, test summary)

## 8. Update integration tests and final verification

- [x] 8.1 Update `MavenMcpServerIntegrationTest` assertions if they check for JSON output format
- [x] 8.2 Verify `BuildResultTest` still passes (JSON serialization of model unchanged)
- [x] 8.3 Run full test suite (`mvn clean test`) — all tests must pass
