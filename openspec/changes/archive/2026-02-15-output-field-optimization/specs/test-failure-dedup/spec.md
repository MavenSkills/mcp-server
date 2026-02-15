## MODIFIED Requirements

### Requirement: Group identical test failures

After stack trace processing, the system SHALL group test failures by root cause extracted from the stack trace. The root cause key SHALL be determined as follows:
1. If the `stackTrace` contains one or more `Caused by:` lines, the key SHALL be the last `Caused by:` line (trimmed).
2. If the `stackTrace` contains no `Caused by:` lines, the key SHALL be the first line of the `message` field (trimmed).
3. If both `stackTrace` and `message` are null, the key SHALL be an empty string.

Null values in the key SHALL be treated as equal to other nulls for grouping purposes.

#### Scenario: Multiple failures with identical root cause
- **WHEN** `maven_test` produces 22 failures where all stack traces end with `Caused by: com.github.dockerjava.api.exception.InternalServerErrorException: Status 500: address already in use`
- **THEN** the `failures` list in `BuildResult` SHALL contain exactly 1 entry for that group

#### Scenario: Failures with same message but different root causes
- **WHEN** two failures share the same `message` text but their stack traces have different last `Caused by:` lines
- **THEN** they SHALL appear as separate entries in the `failures` list

#### Scenario: Failures with different messages but same root cause
- **WHEN** 5 failures have different `message` values (e.g. differing by object hash) but their stack traces share the same last `Caused by:` line
- **THEN** the `failures` list SHALL contain exactly 1 entry for that group

#### Scenario: Simple failures without Caused by chain
- **WHEN** failures have stack traces with no `Caused by:` lines (e.g. simple assertion failures)
- **THEN** grouping SHALL fall back to the first line of the `message` field

#### Scenario: Single failure passes through unchanged
- **WHEN** a failure has a unique root cause (group size = 1)
- **THEN** the `TestFailure` entry SHALL be identical to the original (no modification to any field)
