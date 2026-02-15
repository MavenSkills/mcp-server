## 1. ToolUtils — tail helper

- [x] 1.1 Add `DEFAULT_OUTPUT_TAIL_LINES = 50` constant and `tailLines(String output, int maxLines)` method to `ToolUtils`
- [x] 1.2 Add unit tests for `tailLines` (null input, within limit, exceeds limit, exact boundary)

## 2. StackTraceProcessor — truncate segment headers

- [x] 2.1 Truncate segment headers (top-level + each Caused by) to 200 chars + `...` in `StackTraceProcessor.process()`
- [x] 2.2 Truncate structural header lines (indented Suppressed/Caused by) in `addCollapsedFrames` and `addRootCauseFrames`
- [x] 2.3 Add/update tests: short header unchanged, long header truncated, Caused by header truncated

## 3. SurefireReportParser — truncate message

- [x] 3.1 Truncate `message` attribute to 200 chars + `...` in `extractFailures()`
- [x] 3.2 Add test: long message truncated, short message unchanged, null/empty message stays null

## 4. TestFailureDeduplicator — root cause dedup key

- [x] 4.1 Change dedup key from `(message, stackTrace)` to last `Caused by:` line (fall back to first line of message)
- [x] 4.2 Update existing tests for new dedup key behavior
- [x] 4.3 Add test: failures with different messages but same root cause are grouped

## 5. TestTool — null output when Surefire XML available

- [x] 5.1 Set `output = null` in both BuildResult paths when `surefireResult.isPresent()` (main execution + auto-recompile failure)
- [x] 5.2 Use `ToolUtils.tailLines()` for output when Surefire XML is NOT available
- [x] 5.3 Add/update integration test: output null when surefire XML exists, output tailed when no XML

## 6. CompileTool + CleanTool — tail output

- [x] 6.1 Replace `execResult.stdout()` with `ToolUtils.tailLines(execResult.stdout(), DEFAULT_OUTPUT_TAIL_LINES)` in `CompileTool`
- [x] 6.2 Replace `execResult.stdout()` with `ToolUtils.tailLines(execResult.stdout(), DEFAULT_OUTPUT_TAIL_LINES)` in `CleanTool`

## 7. Remove MavenOutputFilter

- [x] 7.1 Delete `MavenOutputFilter.java` and `MavenOutputFilterTest.java`
- [x] 7.2 Verify no remaining references to `MavenOutputFilter` in codebase
