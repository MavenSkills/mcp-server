## ADDED Requirements

### Requirement: Parse Surefire XML test reports
The parser SHALL read all `TEST-*.xml` files from `target/surefire-reports/` directory. For each file, it SHALL extract the testsuite attributes (tests, failures, errors, skipped) and aggregate them into a single `TestSummary`. Individual test failures and errors SHALL be extracted into `TestFailure` records.

#### Scenario: All tests pass
- **WHEN** the surefire-reports directory contains XML files with zero failures and zero errors
- **THEN** the parser SHALL return a TestSummary with testsFailed=0, testsErrored=0, and an empty failures list

#### Scenario: Tests with failures
- **WHEN** a testsuite XML contains `<testcase>` elements with `<failure>` child elements
- **THEN** the parser SHALL extract each failure's testClass, testMethod, message, and stack trace into a TestFailure record

#### Scenario: Tests with errors
- **WHEN** a testsuite XML contains `<testcase>` elements with `<error>` child elements
- **THEN** the parser SHALL extract each error's testClass, testMethod, message, and stack trace into a TestFailure record

#### Scenario: Multiple XML report files
- **WHEN** the surefire-reports directory contains multiple `TEST-*.xml` files
- **THEN** the parser SHALL aggregate test counts across all files and collect all failures

### Requirement: Truncate stack traces
Stack traces in test failures SHALL be truncated to a configurable maximum number of lines. The default limit SHALL be 50 lines.

#### Scenario: Stack trace exceeds limit
- **WHEN** a test failure has a stack trace with 120 lines and the limit is 50
- **THEN** the returned stack trace SHALL contain only the first 50 lines

#### Scenario: Stack trace within limit
- **WHEN** a test failure has a stack trace with 30 lines and the limit is 50
- **THEN** the returned stack trace SHALL contain all 30 lines unchanged

#### Scenario: Custom stack trace limit
- **WHEN** the parser is called with stackTraceLines=10
- **THEN** stack traces SHALL be truncated to 10 lines

### Requirement: Handle missing surefire-reports directory
If the `target/surefire-reports/` directory does not exist or contains no `TEST-*.xml` files, the parser SHALL return null (indicating reports are not available), allowing the caller to fall back to compilation error parsing.

#### Scenario: No surefire-reports directory
- **WHEN** `target/surefire-reports/` does not exist
- **THEN** the parser SHALL return null

#### Scenario: Empty surefire-reports directory
- **WHEN** `target/surefire-reports/` exists but contains no `TEST-*.xml` files
- **THEN** the parser SHALL return null
