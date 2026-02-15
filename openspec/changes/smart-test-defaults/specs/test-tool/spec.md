## MODIFIED Requirements

### Requirement: Test-only execution mode
The `maven_test` tool SHALL accept a `testOnly` boolean parameter (default: `true`).
- When `testOnly` is `false`, the tool SHALL execute Maven goal `test` (full lifecycle).
- When `testOnly` is `true`, the tool SHALL execute Maven goal `surefire:test` (direct plugin invocation, skipping all lifecycle phases), after performing stale-classes detection and auto-recompile if needed.

The `testOnly` parameter description in `INPUT_SCHEMA` SHALL include proactive guidance for LLM callers about when to use `testOnly=false` — specifically when changes go beyond Java source code (build config, generated source templates, new dependencies, resource files).

All other tool behavior (Surefire XML parsing, stack trace filtering, deduplication, output formatting) SHALL remain identical regardless of the `testOnly` value.

#### Scenario: Default behavior is testOnly
- **WHEN** `maven_test` is called without `testOnly` parameter
- **THEN** the tool SHALL execute in testOnly mode (goal `surefire:test`), including stale detection and auto-recompile

#### Scenario: Full lifecycle explicit
- **WHEN** `maven_test` is called with `testOnly: false`
- **THEN** the tool SHALL execute goal `test` (full Maven lifecycle)

#### Scenario: Test-only mode activated
- **WHEN** `maven_test` is called with `testOnly: true` and `target/test-classes` exists
- **THEN** the tool SHALL execute goal `surefire:test` (direct plugin invocation)

#### Scenario: Test-only mode with test filter
- **WHEN** `maven_test` is called with `testOnly: true` and `testFilter: "MyTest"`
- **THEN** the tool SHALL execute `surefire:test` with `-Dtest=MyTest -DfailIfNoTests=false` arguments

## ADDED Requirements

### Requirement: Auto-recompile on stale classes in testOnly mode
When `testOnly` is `true` and stale classes are detected (per the stale-classes-detection capability), the tool SHALL automatically recompile by executing `compiler:compile compiler:testCompile` via Maven before running `surefire:test`.

If the auto-recompile succeeds, the tool SHALL proceed with `surefire:test` execution.

If the auto-recompile fails, the tool SHALL return a compilation error result and SHALL NOT execute `surefire:test`.

The auto-recompile SHALL NOT run when `testOnly` is `false` (the full lifecycle handles compilation).

#### Scenario: Stale detected — auto-recompile succeeds
- **WHEN** `testOnly` is `true` and stale classes are detected and `compiler:compile compiler:testCompile` succeeds
- **THEN** the tool SHALL proceed with `surefire:test` execution

#### Scenario: Stale detected — auto-recompile fails
- **WHEN** `testOnly` is `true` and stale classes are detected and `compiler:compile compiler:testCompile` fails
- **THEN** the tool SHALL return a compilation error result
- **AND** the tool SHALL NOT execute `surefire:test`

#### Scenario: No stale classes — no recompile
- **WHEN** `testOnly` is `true` and no stale classes are detected
- **THEN** the tool SHALL proceed directly with `surefire:test` without invoking the compiler

#### Scenario: Full lifecycle mode — no stale check
- **WHEN** `testOnly` is `false`
- **THEN** the tool SHALL NOT perform stale-classes detection or auto-recompile

### Requirement: Contextual note in testOnly mode
When `testOnly` is `true`, the tool SHALL include a `note` field in the `BuildResult` JSON response explaining the execution context.

The `note` field SHALL be a nullable `String` on the `BuildResult` record, omitted from JSON when null (via `@JsonInclude(NON_NULL)`).

When `testOnly` is `false`, the `note` field SHALL be null (omitted from response).

#### Scenario: testOnly without stale classes
- **WHEN** `testOnly` is `true` and no stale classes were detected
- **THEN** the `note` field SHALL contain: `"Ran in testOnly mode (surefire:test). Lifecycle phases (generate-sources, compile) were skipped. If tests fail unexpectedly, re-run with testOnly=false for a full build."`

#### Scenario: testOnly with auto-recompile
- **WHEN** `testOnly` is `true` and stale classes were detected and auto-recompile succeeded
- **THEN** the `note` field SHALL contain: `"Ran in testOnly mode. Stale sources detected — auto-recompiled via compiler:compile compiler:testCompile (generate-sources was skipped). If tests still fail unexpectedly, re-run with testOnly=false for a full build."`

#### Scenario: Full lifecycle — no note
- **WHEN** `testOnly` is `false`
- **THEN** the `note` field SHALL be null (omitted from JSON)
