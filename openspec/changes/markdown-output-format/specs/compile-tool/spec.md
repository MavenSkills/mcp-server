## MODIFIED Requirements

### Requirement: MCP tool maven_compile
The server SHALL register an MCP tool named `maven_compile` with description "Compile a Maven project. Returns structured compilation errors with file, line, column, and message." The tool SHALL invoke `mvn compile -B` via MavenRunner and return a Markdown-formatted result via `MarkdownFormatter.format(buildResult, "Compile")`.

#### Scenario: Successful compilation with no warnings
- **WHEN** the agent calls `maven_compile` with no arguments and compilation succeeds with no warnings
- **THEN** the tool SHALL return a Markdown string: `Compile SUCCESS ({duration}s)`

#### Scenario: Compilation failure with errors
- **WHEN** the agent calls `maven_compile` and javac produces errors
- **THEN** the tool SHALL return a Markdown string starting with `Compile FAILURE ({duration}s) — N errors` followed by errors grouped by file under `###` headers

#### Scenario: Successful compilation with warnings
- **WHEN** compilation succeeds but javac produces deprecation or unchecked warnings
- **THEN** the tool SHALL return a Markdown string: `Compile SUCCESS ({duration}s) — N warnings`

### Requirement: maven_compile response format
The tool SHALL return a `CallToolResult` containing a single `TextContent` with a Markdown-formatted string produced by `MarkdownFormatter`. The response is plain text optimized for LLM consumption, not JSON.

#### Scenario: Response is Markdown in TextContent
- **WHEN** the tool completes execution
- **THEN** the `CallToolResult` SHALL contain one `TextContent` element whose text is a Markdown-formatted build result

### Requirement: maven_compile raw output on failure only
The raw Maven output SHALL only appear in the Markdown response when `status` is `FAILURE` and no structured errors are available. When structured errors exist, they SHALL be rendered as grouped error listings instead.

#### Scenario: Success omits raw output
- **WHEN** compilation succeeds
- **THEN** the Markdown response SHALL NOT contain raw Maven output

#### Scenario: Failure with structured errors
- **WHEN** compilation fails and errors are parsed
- **THEN** the Markdown response SHALL contain errors grouped by file, not raw output
