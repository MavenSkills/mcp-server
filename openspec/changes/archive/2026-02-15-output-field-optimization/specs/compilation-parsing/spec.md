## MODIFIED Requirements

### Requirement: Parse javac compilation errors from Maven stdout
The parser SHALL extract compilation errors from Maven's stdout using regex matching. Each error SHALL be parsed into a structured record containing: file path, line number, column number (nullable), error message, and severity. The parser SHALL match the standard javac error format produced by Maven Compiler Plugin: `[ERROR] /absolute/path/File.java:[line,col] message`.

The `CompileTool` SHALL use `ToolUtils.tailLines()` with `DEFAULT_OUTPUT_TAIL_LINES` on failure output instead of passing raw `execResult.stdout()`. The structured `errors[]` and `warnings[]` fields contain parsed data; the `output` field is a fallback for context not captured by the parser.

#### Scenario: Single compilation error
- **WHEN** Maven stdout contains `[ERROR] /home/user/project/src/main/java/com/example/Foo.java:[42,15] cannot find symbol`
- **THEN** the parser SHALL return one error with file=`src/main/java/com/example/Foo.java`, line=42, column=15, message=`cannot find symbol`, severity=`ERROR`

#### Scenario: Multiple compilation errors
- **WHEN** Maven stdout contains 3 lines matching the error pattern
- **THEN** the parser SHALL return a list of 3 `CompilationError` records in order of appearance

#### Scenario: No compilation errors
- **WHEN** Maven stdout contains no lines matching the error pattern (successful build)
- **THEN** the parser SHALL return an empty list

#### Scenario: Failure output is tailed
- **WHEN** `maven_compile` fails and Maven stdout is 500 lines
- **THEN** the `output` field in `BuildResult` SHALL contain the last 50 lines of stdout
- **AND** the `errors` field SHALL contain all structured compilation errors regardless of tail
