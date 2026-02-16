## ADDED Requirements

### Requirement: MarkdownFormatter formats BuildResult as Markdown
`MarkdownFormatter` SHALL provide a static method `format(BuildResult result, String operation)` that returns a Markdown-formatted string. The method SHALL be a pure function with no side effects.

#### Scenario: Clean success
- **WHEN** `format()` is called with a SUCCESS result, duration 800ms, and operation "Clean"
- **THEN** it SHALL return `Clean SUCCESS (0.8s)`

#### Scenario: Compile success no warnings
- **WHEN** `format()` is called with a SUCCESS result, duration 3200ms, operation "Compile", and null/empty errors and warnings
- **THEN** it SHALL return `Compile SUCCESS (3.2s)`

#### Scenario: Timeout
- **WHEN** `format()` is called with a TIMEOUT result, duration 30000ms, and operation "Compile"
- **THEN** it SHALL return `Compile TIMEOUT (30.0s)`

### Requirement: Duration formatting
Duration SHALL be formatted as seconds with exactly 1 decimal place (`milliseconds / 1000.0`, format `"%.1fs"`).

#### Scenario: Sub-second duration
- **WHEN** duration is 150ms
- **THEN** it SHALL render as `0.2s`

#### Scenario: Zero duration
- **WHEN** duration is 0ms
- **THEN** it SHALL render as `0.0s`

#### Scenario: Multi-second duration
- **WHEN** duration is 5100ms
- **THEN** it SHALL render as `5.1s`

### Requirement: Warning count in header
When warnings are present and there are no errors and no test summary, the header SHALL include a warning count suffix: `— N warning(s)`.

#### Scenario: Multiple warnings
- **WHEN** result has 2 warnings, no errors, and no test summary
- **THEN** header SHALL end with `— 2 warnings`

#### Scenario: Single warning
- **WHEN** result has 1 warning, no errors, and no test summary
- **THEN** header SHALL end with `— 1 warning`

### Requirement: Error count in header
When errors are present and there is no test summary, the header SHALL include an error count suffix: `— N error(s)`.

#### Scenario: Multiple errors
- **WHEN** result has 3 errors and no test summary
- **THEN** header SHALL end with `— 3 errors`

#### Scenario: Single error
- **WHEN** result has 1 error and no test summary
- **THEN** header SHALL end with `— 1 error`

### Requirement: Compilation errors grouped by file
When `errors` is non-empty, errors SHALL be rendered after the header, grouped by file path in encounter order. Each file group SHALL have a `### {file}` header. Each error SHALL be rendered as `- L{line}[:{column}] — {message}`.

#### Scenario: Errors in multiple files
- **WHEN** result contains errors in `src/main/java/Foo.java` (lines 42:15, 58) and `src/main/java/Baz.java` (line 12:8)
- **THEN** output SHALL contain two `###` sections grouped by file, with errors listed under each

#### Scenario: Column is optional
- **WHEN** an error has line 58 and null column
- **THEN** it SHALL render as `- L58 — {message}` (no colon suffix)

### Requirement: Test summary in header
When `summary` is present, the header SHALL include test counts: `— N run, M failed[, K skipped]`. Skipped count SHALL only appear when greater than zero. Test summary takes priority over error and warning counts.

#### Scenario: All tests pass
- **WHEN** summary has 42 run, 0 failed, 0 skipped
- **THEN** header SHALL end with `— 42 run, 0 failed`

#### Scenario: Tests with skipped
- **WHEN** summary has 42 run, 0 failed, 3 skipped
- **THEN** header SHALL end with `— 42 run, 0 failed, 3 skipped`

#### Scenario: Tests with failures and skipped
- **WHEN** summary has 42 run, 2 failed, 1 skipped
- **THEN** header SHALL end with `— 42 run, 2 failed, 1 skipped`

### Requirement: Test failures rendered as sections
Each test failure SHALL be rendered as a `### FAILED: {ShortClass}#{method}` section followed by the failure message. The class name SHALL be shortened by stripping the package prefix.

#### Scenario: Failure with stack trace
- **WHEN** a failure has class `com.example.FooTest`, method `shouldCalc`, message, and stack trace
- **THEN** output SHALL contain `### FAILED: FooTest#shouldCalc` followed by the message, then stack trace lines indented with 2 spaces

#### Scenario: Failure without stack trace
- **WHEN** a failure has null stack trace
- **THEN** only the message SHALL appear after the `### FAILED:` header

#### Scenario: Failure with test output
- **WHEN** a failure has non-blank `testOutput`
- **THEN** after the stack trace (or message if no trace), output SHALL contain `  Test output:` followed by each output line indented with 2 spaces

### Requirement: Stack traces and test output indented with 2 spaces
Stack trace lines and test output lines SHALL each be prefixed with exactly 2 spaces. No fenced code blocks SHALL be used.

#### Scenario: Multi-line stack trace
- **WHEN** stack trace contains `at FooTest.shouldCalc(FooTest.java:25)\nat Calculator.total(Calculator.java:18)`
- **THEN** each line SHALL be rendered on its own line with 2-space prefix

### Requirement: Raw output for failure without structured data
When `output` is non-blank and there are no structured errors or failures, the raw output SHALL be rendered with each line indented by 2 spaces, separated from the header by a blank line.

#### Scenario: Clean failure with raw output
- **WHEN** result has FAILURE status, non-blank `output`, and no errors/failures
- **THEN** output lines SHALL appear after a blank line, each prefixed with 2 spaces

### Requirement: Note rendered as blockquote
When `note` is non-blank, it SHALL be rendered as `> {note}` at the end of the output, separated from preceding content by a blank line.

#### Scenario: Success with note
- **WHEN** result has SUCCESS status and a non-blank note
- **THEN** the note SHALL appear as `> {note}` after a blank line

#### Scenario: Failure with note and failures
- **WHEN** result has failures and a note
- **THEN** the note SHALL appear after all failure sections, separated by a blank line

### Requirement: Header detail suffix priority
The header SHALL include at most one detail suffix, chosen by priority: (1) test summary, (2) error count, (3) warning count.

#### Scenario: Summary takes priority over errors
- **WHEN** result has both a test summary and compilation errors
- **THEN** the header SHALL show test summary counts, not error count
