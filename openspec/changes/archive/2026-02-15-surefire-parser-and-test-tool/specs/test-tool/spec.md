## ADDED Requirements

### Requirement: MCP tool maven_test
The server SHALL register an MCP tool named `maven_test` that runs Maven tests and returns structured results parsed from Surefire XML reports.

#### Scenario: Run all tests successfully
- **WHEN** the agent calls `maven_test` with no parameters and all tests pass
- **THEN** the tool SHALL return `status: "SUCCESS"`, a TestSummary with testsFailed=0, an empty failures list, and no output field

#### Scenario: Run all tests with failures
- **WHEN** the agent calls `maven_test` and some tests fail
- **THEN** the tool SHALL return `status: "FAILURE"`, a TestSummary with failure counts, a failures list with TestFailure details, and the raw output

### Requirement: Test filtering via testFilter parameter
The tool SHALL accept an optional `testFilter` string parameter that maps to Maven's `-Dtest=<value>` argument.

#### Scenario: Filter by class name
- **WHEN** the agent calls `maven_test` with `testFilter: "MyTest"`
- **THEN** the server SHALL execute `<maven> test -B -Dtest=MyTest`

#### Scenario: Filter by method
- **WHEN** the agent calls `maven_test` with `testFilter: "MyTest#shouldWork"`
- **THEN** the server SHALL execute `<maven> test -B -Dtest=MyTest#shouldWork`

#### Scenario: Filter multiple classes
- **WHEN** the agent calls `maven_test` with `testFilter: "MyTest,OtherTest"`
- **THEN** the server SHALL execute `<maven> test -B -Dtest=MyTest,OtherTest`

#### Scenario: No filter
- **WHEN** the agent calls `maven_test` without `testFilter`
- **THEN** the server SHALL execute `<maven> test -B` (run all tests)

### Requirement: Configurable stack trace truncation
The tool SHALL accept an optional `stackTraceLines` integer parameter to control stack trace truncation. Default SHALL be 50.

#### Scenario: Custom stack trace limit
- **WHEN** the agent calls `maven_test` with `stackTraceLines: 20`
- **THEN** stack traces in TestFailure records SHALL be truncated to 20 lines

### Requirement: Compilation failure fallback
If Maven test execution fails and no Surefire XML reports exist (indicating a compilation failure before tests ran), the tool SHALL fall back to parsing stdout for compilation errors and return them in the `errors`/`warnings` fields instead of `summary`/`failures`.

#### Scenario: Tests fail to compile
- **WHEN** Maven exits with non-zero code and `target/surefire-reports/` has no XML files
- **THEN** the tool SHALL parse compilation errors from stdout and return them in the `errors` field with `status: "FAILURE"`

#### Scenario: Tests fail at runtime
- **WHEN** Maven exits with non-zero code and Surefire XML reports exist
- **THEN** the tool SHALL parse the XML reports and return test results in `summary`/`failures` fields

### Requirement: maven_test accepts additional Maven arguments
The tool SHALL accept an optional `args` parameter (array of strings) appended to the Maven command line.

#### Scenario: Extra arguments
- **WHEN** the agent calls `maven_test` with `args: ["-X"]`
- **THEN** the server SHALL append `-X` to the Maven command
