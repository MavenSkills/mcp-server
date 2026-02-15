## ADDED Requirements

### Requirement: Tail helper for raw Maven output

`ToolUtils` SHALL provide a static method `tailLines(String output, int maxLines)` that returns the last N lines of a string. If the input has fewer than N lines, it SHALL return the input unchanged. If the input is null, it SHALL return null.

#### Scenario: Output exceeds limit
- **WHEN** `tailLines` is called with a 200-line string and `maxLines=50`
- **THEN** it SHALL return the last 50 lines of the string

#### Scenario: Output within limit
- **WHEN** `tailLines` is called with a 30-line string and `maxLines=50`
- **THEN** it SHALL return the input unchanged

#### Scenario: Null input
- **WHEN** `tailLines` is called with null input
- **THEN** it SHALL return null

### Requirement: Default tail lines constant

`ToolUtils` SHALL define a constant `DEFAULT_OUTPUT_TAIL_LINES = 50`.

#### Scenario: Constant value
- **WHEN** code references `ToolUtils.DEFAULT_OUTPUT_TAIL_LINES`
- **THEN** the value SHALL be 50

### Requirement: MavenOutputFilter removal

The `MavenOutputFilter` class and its tests SHALL be removed from the codebase. All call sites SHALL be replaced with `ToolUtils.tailLines()`.

#### Scenario: No references to MavenOutputFilter remain
- **WHEN** the codebase is searched for `MavenOutputFilter`
- **THEN** no references SHALL exist in production or test code
