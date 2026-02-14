## ADDED Requirements

### Requirement: MCP tool maven_clean
The server SHALL register an MCP tool named `maven_clean` with description "Clean the Maven project build directory (target/)." The tool SHALL invoke `mvn clean -B` via MavenRunner and return a structured JSON result. No output parsing is needed — only success/failure status.

#### Scenario: Successful clean
- **WHEN** the agent calls `maven_clean` and the clean operation succeeds
- **THEN** the tool SHALL return `status: "SUCCESS"`, `duration` in ms, and no other fields

#### Scenario: Failed clean
- **WHEN** the agent calls `maven_clean` and the operation fails (non-zero exit code)
- **THEN** the tool SHALL return `status: "FAILURE"`, `duration` in ms, and `output` containing raw Maven stdout

### Requirement: maven_clean accepts additional Maven arguments
The tool SHALL accept an optional `args` parameter (array of strings) appended to the Maven command line.

#### Scenario: Clean with extra arguments
- **WHEN** the agent calls `maven_clean` with `args: ["-X"]`
- **THEN** the server SHALL execute `<maven> clean -B -X`
