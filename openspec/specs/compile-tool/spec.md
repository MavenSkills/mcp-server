## ADDED Requirements

### Requirement: MCP tool maven_compile
The server SHALL register an MCP tool named `maven_compile` with description "Compile a Maven project. Returns structured compilation errors with file, line, column, and message." The tool SHALL invoke `mvn compile -B` via MavenRunner and return a structured JSON result.

#### Scenario: Successful compilation with no warnings
- **WHEN** the agent calls `maven_compile` with no arguments and compilation succeeds with no warnings
- **THEN** the tool SHALL return a JSON response with `status: "SUCCESS"`, `duration` in ms, empty `errors` array, empty `warnings` array, and no `output` field

#### Scenario: Compilation failure with errors
- **WHEN** the agent calls `maven_compile` and javac produces errors
- **THEN** the tool SHALL return `status: "FAILURE"`, populated `errors` array with structured `CompilationError` objects, and `output` containing raw Maven stdout

#### Scenario: Successful compilation with warnings
- **WHEN** compilation succeeds but javac produces deprecation or unchecked warnings
- **THEN** the tool SHALL return `status: "SUCCESS"`, empty `errors` array, populated `warnings` array, and no `output` field

### Requirement: maven_compile accepts additional Maven arguments
The tool SHALL accept an optional `args` parameter (array of strings) that SHALL be appended to the Maven command line after the `-B` flag.

#### Scenario: Extra arguments passed
- **WHEN** the agent calls `maven_compile` with `args: ["-DskipFrontend", "-Pdev"]`
- **THEN** the server SHALL execute `<maven> compile -B -DskipFrontend -Pdev`

#### Scenario: No extra arguments
- **WHEN** the agent calls `maven_compile` without the `args` parameter
- **THEN** the server SHALL execute `<maven> compile -B` with no extra flags

### Requirement: maven_compile response format
The tool SHALL return a `CallToolResult` containing a single `TextContent` with a JSON-serialized `BuildResult`. Null fields SHALL be omitted from the JSON output.

#### Scenario: Response is valid JSON in TextContent
- **WHEN** the tool completes execution
- **THEN** the `CallToolResult` SHALL contain one `TextContent` element whose text is valid JSON parseable as a `BuildResult` object

### Requirement: maven_compile raw output on failure only
The `output` field in the response SHALL only be populated when `status` is `FAILURE`. On success, the field SHALL be null (omitted from JSON).

#### Scenario: Success omits output
- **WHEN** compilation succeeds
- **THEN** the JSON response SHALL NOT contain an `output` field

#### Scenario: Failure includes output
- **WHEN** compilation fails
- **THEN** the JSON response SHALL contain an `output` field with the raw Maven stdout
