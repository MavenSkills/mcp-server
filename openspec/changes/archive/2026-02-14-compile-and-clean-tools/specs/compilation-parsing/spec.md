## ADDED Requirements

### Requirement: Parse javac compilation errors from Maven stdout
The parser SHALL extract compilation errors from Maven's stdout using regex matching. Each error SHALL be parsed into a structured record containing: file path, line number, column number (nullable), error message, and severity. The parser SHALL match the standard javac error format produced by Maven Compiler Plugin: `[ERROR] /absolute/path/File.java:[line,col] message`.

#### Scenario: Single compilation error
- **WHEN** Maven stdout contains `[ERROR] /home/user/project/src/main/java/com/example/Foo.java:[42,15] cannot find symbol`
- **THEN** the parser SHALL return one error with file=`src/main/java/com/example/Foo.java`, line=42, column=15, message=`cannot find symbol`, severity=`ERROR`

#### Scenario: Multiple compilation errors
- **WHEN** Maven stdout contains 3 lines matching the error pattern
- **THEN** the parser SHALL return a list of 3 `CompilationError` records in order of appearance

#### Scenario: No compilation errors
- **WHEN** Maven stdout contains no lines matching the error pattern (successful build)
- **THEN** the parser SHALL return an empty list

### Requirement: Parse javac compilation warnings from Maven stdout
The parser SHALL extract compilation warnings using the pattern `[WARNING] /path/File.java:[line,col] message`. Warnings SHALL use severity `WARNING`.

#### Scenario: Deprecation warning
- **WHEN** Maven stdout contains `[WARNING] /path/Foo.java:[10,5] [deprecation] method doStuff() is deprecated`
- **THEN** the parser SHALL return one warning with severity=`WARNING` and the full message text

#### Scenario: Mixed errors and warnings
- **WHEN** Maven stdout contains both `[ERROR]` and `[WARNING]` compilation lines
- **THEN** the parser SHALL return both errors and warnings, each with the correct severity

### Requirement: Normalize file paths to project-relative
Compilation error file paths produced by Maven/javac are absolute paths. The parser SHALL convert them to paths relative to the project root directory.

#### Scenario: Absolute path normalization
- **WHEN** an error contains file path `/home/user/my-project/src/main/java/com/example/Foo.java` and the project root is `/home/user/my-project`
- **THEN** the returned file path SHALL be `src/main/java/com/example/Foo.java`

#### Scenario: Path already relative or not under project root
- **WHEN** the file path cannot be relativized against the project root
- **THEN** the parser SHALL return the original absolute path unchanged

### Requirement: Handle missing column number
Some compiler error formats may omit the column number (e.g., `[ERROR] /path/File.java:[42] message`). The parser SHALL handle this case gracefully.

#### Scenario: Error without column
- **WHEN** Maven stdout contains `[ERROR] /path/Foo.java:[42] some error message`
- **THEN** the parser SHALL return an error with line=42, column=null, and the message

### Requirement: Ignore non-compilation lines
Maven stdout contains many lines that are not compilation errors (e.g., `[INFO] Compiling 42 source files`, `[ERROR] BUILD FAILURE`). The parser SHALL only extract lines matching the file:line compilation pattern.

#### Scenario: BUILD FAILURE line is not a compilation error
- **WHEN** Maven stdout contains `[ERROR] BUILD FAILURE` and `[ERROR]  -> [Help 1]`
- **THEN** the parser SHALL NOT include these as compilation errors (they don't match the file:[line,col] pattern)
