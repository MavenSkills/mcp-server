## ADDED Requirements

### Requirement: Truncate segment headers in StackTraceProcessor

When `StackTraceProcessor.process()` emits a segment header line (top-level exception or `Caused by:` line), it SHALL truncate the header to a maximum of 200 characters. If the original header exceeds 200 characters, the output SHALL be the first 200 characters followed by `...` (ellipsis).

This applies to all segment headers: the top-level exception line and each `Caused by:` line.

#### Scenario: Short header passes through unchanged
- **WHEN** a segment header is `java.lang.NullPointerException: foo is null` (44 chars)
- **THEN** the output SHALL contain the header unchanged

#### Scenario: Long header is truncated
- **WHEN** a segment header is `java.lang.IllegalStateException: Failed to load ApplicationContext for [WebMergedContextConfiguration@... 3800 chars ...]` (3800+ chars)
- **THEN** the output SHALL contain the first 200 characters followed by `...`

#### Scenario: Caused by header is also truncated
- **WHEN** a `Caused by:` segment header exceeds 200 characters
- **THEN** it SHALL be truncated to 200 characters followed by `...`

#### Scenario: Indented structural headers (Suppressed, nested Caused by) are truncated
- **WHEN** a structural header line (indented `Suppressed:` or indented `Caused by:`) in the frame list exceeds 200 characters
- **THEN** it SHALL be truncated to 200 characters followed by `...`

### Requirement: Truncate message in SurefireReportParser

When `SurefireReportParser` extracts the `message` attribute from a Surefire XML `<failure>` or `<error>` element, it SHALL truncate the message to a maximum of 200 characters. If the original message exceeds 200 characters, the stored value SHALL be the first 200 characters followed by `...`.

#### Scenario: Short message passes through unchanged
- **WHEN** the XML `message` attribute is `expected:<200> but was:<404>` (30 chars)
- **THEN** the `TestFailure.message` field SHALL contain the value unchanged

#### Scenario: Long Spring context message is truncated
- **WHEN** the XML `message` attribute is `Failed to load ApplicationContext for [WebMergedContextConfiguration@... 3800 chars ...]`
- **THEN** the `TestFailure.message` field SHALL contain the first 200 characters followed by `...`

#### Scenario: Null message remains null
- **WHEN** the XML `message` attribute is absent or empty
- **THEN** the `TestFailure.message` field SHALL be null
