## ADDED Requirements

### Requirement: Group identical test failures

After stack trace processing, the system SHALL group test failures by the composite key `(message, stackTrace)` using exact string equality. Null values in either field SHALL be treated as equal to other nulls for grouping purposes.

#### Scenario: Multiple failures with identical message and stackTrace

- **WHEN** `maven_test` produces 11 failures where all share the same `message` and `stackTrace` values
- **THEN** the `failures` list in `BuildResult` SHALL contain exactly 1 entry for that group

#### Scenario: Failures with different messages are not grouped

- **WHEN** `maven_test` produces 3 failures with message "Error A" and 2 failures with message "Error B" (all sharing the same `stackTrace`)
- **THEN** the `failures` list SHALL contain 2 entries (one per distinct message)

#### Scenario: Failures with different stackTraces are not grouped

- **WHEN** two failures share the same `message` but have different `stackTrace` values
- **THEN** they SHALL appear as separate entries in the `failures` list

#### Scenario: Single failure passes through unchanged

- **WHEN** a failure has a unique `(message, stackTrace)` combination (group size = 1)
- **THEN** the `TestFailure` entry SHALL be identical to the original (no modification to any field)

### Requirement: Consolidated testMethod field for grouped failures

For groups containing more than one failure, the system SHALL produce a single `TestFailure` whose `testMethod` field summarizes all methods in the group. The format SHALL be: the first N method names joined by `, `, followed by ` (+M more)` when the group exceeds N methods, where N = 3.

#### Scenario: Group of 3 or fewer methods

- **WHEN** a group contains methods `["testA", "testB", "testC"]`
- **THEN** `testMethod` SHALL be `"testA, testB, testC"`

#### Scenario: Group of more than 3 methods

- **WHEN** a group contains methods `["testA", "testB", "testC", "testD", "testE"]`
- **THEN** `testMethod` SHALL be `"testA, testB, testC (+2 more)"`

#### Scenario: Group of 2 methods

- **WHEN** a group contains methods `["testAlpha", "testBeta"]`
- **THEN** `testMethod` SHALL be `"testAlpha, testBeta"`

### Requirement: Consolidated testClass field for grouped failures

For groups containing failures from the same test class, `testClass` SHALL use that class name. For groups spanning multiple test classes, `testClass` SHALL list distinct class names joined by `, `, following the same N=3 threshold as `testMethod`.

#### Scenario: All failures in group from same class

- **WHEN** a group contains 5 failures all with `testClass = "com.example.BootstrapTest"`
- **THEN** the grouped entry's `testClass` SHALL be `"com.example.BootstrapTest"`

#### Scenario: Failures in group from multiple classes

- **WHEN** a group contains failures from classes `["com.example.FooTest", "com.example.BarTest"]`
- **THEN** `testClass` SHALL be `"com.example.FooTest, com.example.BarTest"`

### Requirement: Consolidated testOutput field for grouped failures

For grouped failures, `testOutput` SHALL be concatenated from all non-null individual outputs, separated by a newline delimiter. If all individual `testOutput` values are null, the grouped `testOutput` SHALL be null.

#### Scenario: Some failures have testOutput

- **WHEN** a group of 3 failures has testOutput values `["output1", null, "output3"]`
- **THEN** the grouped `testOutput` SHALL contain `"output1"` and `"output3"` separated by a delimiter

#### Scenario: All failures have null testOutput

- **WHEN** a group of 4 failures all have `testOutput = null`
- **THEN** the grouped `testOutput` SHALL be null

### Requirement: Preserve original failure order

The system SHALL preserve the relative order of groups based on the position of the first failure in each group within the original list.

#### Scenario: Order preservation

- **WHEN** failures arrive in order `[F1(groupA), F2(groupB), F3(groupA), F4(groupC)]`
- **THEN** the deduplicated list SHALL be ordered `[groupA, groupB, groupC]` (groupA first because F1 appeared before F2)

### Requirement: No schema changes to BuildResult or TestFailure

The deduplication step SHALL NOT modify the `BuildResult` record or `TestFailure` record definitions. It operates as a `List<TestFailure> → List<TestFailure>` transformation.

#### Scenario: JSON output structure unchanged

- **WHEN** an agent parses the `maven_test` JSON response after deduplication is enabled
- **THEN** the JSON structure SHALL match the existing `BuildResult` schema with no new or removed fields
