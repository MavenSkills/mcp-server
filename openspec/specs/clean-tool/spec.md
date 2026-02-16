## ADDED Requirements

### Requirement: MCP tool maven_clean
The server SHALL register an MCP tool named `maven_clean` with description "Clean the Maven project build directory (target/)." The tool SHALL invoke `mvn clean -B` via MavenRunner and return a Markdown-formatted result via `MarkdownFormatter.format(buildResult, "Clean")`.

#### Scenario: Successful clean
- **WHEN** the agent calls `maven_clean` and the clean operation succeeds
- **THEN** the tool SHALL return a Markdown string: `Clean SUCCESS ({duration}s)`

#### Scenario: Failed clean
- **WHEN** the agent calls `maven_clean` and the operation fails (non-zero exit code)
- **THEN** the tool SHALL return a Markdown string starting with `Clean FAILURE ({duration}s)` followed by raw Maven output indented with 2 spaces

### Requirement: maven_clean accepts additional Maven arguments
The tool SHALL accept an optional `args` parameter (array of strings) appended to the Maven command line.

#### Scenario: Clean with extra arguments
- **WHEN** the agent calls `maven_clean` with `args: ["-X"]`
- **THEN** the server SHALL execute `<maven> clean -B -X`
