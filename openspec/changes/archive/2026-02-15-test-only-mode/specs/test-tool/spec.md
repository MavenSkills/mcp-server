## ADDED Requirements

### Requirement: Test-only execution mode
The `maven_test` tool SHALL accept a `testOnly` boolean parameter (default: `false`).
- When `testOnly` is `false`, the tool SHALL execute Maven goal `test` (full lifecycle — current behavior).
- When `testOnly` is `true`, the tool SHALL execute Maven goal `surefire:test` (direct plugin invocation, skipping all lifecycle phases).

All other tool behavior (Surefire XML parsing, stack trace filtering, deduplication, output formatting) SHALL remain identical regardless of the `testOnly` value.

#### Scenario: Default behavior preserved
- **WHEN** `maven_test` is called without `testOnly` parameter
- **THEN** the tool SHALL execute goal `test` (full Maven lifecycle)

#### Scenario: Default behavior explicit
- **WHEN** `maven_test` is called with `testOnly: false`
- **THEN** the tool SHALL execute goal `test` (full Maven lifecycle)

#### Scenario: Test-only mode activated
- **WHEN** `maven_test` is called with `testOnly: true` and `target/test-classes` exists
- **THEN** the tool SHALL execute goal `surefire:test` (direct plugin invocation)

#### Scenario: Test-only mode with test filter
- **WHEN** `maven_test` is called with `testOnly: true` and `testFilter: "MyTest"`
- **THEN** the tool SHALL execute `surefire:test` with `-Dtest=MyTest -DfailIfNoTests=false` arguments

### Requirement: Pre-flight compilation guard for test-only mode
When `testOnly` is `true`, the tool SHALL verify that `target/test-classes` directory exists in the project directory before executing `surefire:test`.

If the directory does not exist, the tool SHALL return an error response (`isError: true`) with the message: `"Project not compiled. Run maven_compile first or set testOnly=false."`

The tool SHALL NOT invoke Maven when the guard check fails.

#### Scenario: Guard passes — test-classes exist
- **WHEN** `maven_test` is called with `testOnly: true` and `target/test-classes` directory exists
- **THEN** the tool SHALL proceed with `surefire:test` execution

#### Scenario: Guard fails — test-classes missing
- **WHEN** `maven_test` is called with `testOnly: true` and `target/test-classes` directory does not exist
- **THEN** the tool SHALL return an error with message `"Project not compiled. Run maven_compile first or set testOnly=false."`
- **AND** the tool SHALL NOT spawn a Maven process

#### Scenario: Guard not applied in default mode
- **WHEN** `maven_test` is called with `testOnly: false` (or omitted)
- **THEN** the tool SHALL NOT check for `target/test-classes` existence (Maven lifecycle handles compilation)
