## Why

Tool responses are serialized as JSON strings inside MCP `TextContent`. LLMs consuming these responses waste tokens on structural syntax (braces, quotes, repeated keys) and find nested JSON-in-JSON harder to parse than prose. Switching to Markdown reduces token usage by ~50% and improves LLM readability — with no loss of information.

## What Changes

- **New `MarkdownFormatter` class** — pure function converting `BuildResult` to a compact Markdown string with operation status, duration, grouped errors, test failures, and notes
- **BREAKING**: `CleanTool`, `CompileTool`, `TestTool` replace `objectMapper.writeValueAsString(buildResult)` with `MarkdownFormatter.format(buildResult, operation)` — tool responses switch from JSON to Markdown
- No changes to MCP wire protocol (`CallToolResult` with `TextContent`), tool input parameters, internal model classes (`BuildResult`, `CompilationError`, `TestFailure`, `TestSummary`), or parsers

## Capabilities

### New Capabilities
- `markdown-formatting`: Defines the Markdown output format specification — header line format, duration formatting, error grouping by file, test failure sections, notes as blockquotes, and raw output indentation rules

### Modified Capabilities
- `clean-tool`: Response format changes from JSON-serialized `BuildResult` to Markdown string
- `compile-tool`: Response format changes from JSON-serialized `BuildResult` to Markdown string; removes requirement for "valid JSON parseable as a BuildResult object"
- `test-tool`: Response format changes from JSON-serialized `BuildResult` to Markdown string

## Impact

- **Tool output contract**: Any tooling parsing JSON responses programmatically will break. Since the only consumers are LLMs via MCP, this is acceptable.
- **Affected code**: `CleanTool.java`, `CompileTool.java`, `TestTool.java` (one-line change each), plus new `MarkdownFormatter.java`
- **Test impact**: Tool tests asserting JSON patterns need updating to assert Markdown patterns; `BuildResultTest` (JSON serialization) remains valid as `BuildResult` model is unchanged
- **No dependency changes**: Uses only `StringBuilder` and existing model classes
