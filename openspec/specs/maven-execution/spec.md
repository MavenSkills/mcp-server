# Maven Execution

### Requirement: Maven executable detection
The server SHALL detect the Maven executable to use for all operations. Detection logic:
1. Check if `./mvnw` exists in the project directory and is executable — if yes, use it
2. Otherwise, check if `mvn` is available on the system PATH — if yes, use it
3. If neither is found, report an error

Detection SHALL occur at startup (as part of eager validation). The detected executable SHALL NOT be cached across tool invocations — the server SHALL re-verify the executable exists on each invocation (filesystem check cost is negligible).

#### Scenario: Maven Wrapper present in project
- **WHEN** the project directory contains an executable `./mvnw` file
- **THEN** the server SHALL use `./mvnw` as the Maven executable

#### Scenario: No wrapper, system Maven available
- **WHEN** the project directory does not contain `./mvnw` but `mvn` is available on PATH
- **THEN** the server SHALL use the system `mvn` as the Maven executable

#### Scenario: Neither wrapper nor system Maven available
- **WHEN** the project directory has no `./mvnw` and `mvn` is not on PATH
- **THEN** the server SHALL report a `MavenNotFoundException` with a descriptive message

#### Scenario: Wrapper exists but is not executable
- **WHEN** the project directory contains `./mvnw` but it lacks execute permission
- **THEN** the server SHALL fall back to system `mvn` (same as if wrapper doesn't exist)

### Requirement: Maven process execution
The server SHALL execute Maven commands as child processes using `ProcessBuilder`. The process SHALL:
1. Set the working directory to the project directory
2. Always include the `-B` flag (batch mode) for clean, non-interactive output
3. Accept a Maven goal (String) and optional additional arguments (List of String)
4. Inherit the server process environment (JAVA_HOME, PATH, etc.)

#### Scenario: Execute Maven goal with default settings
- **WHEN** a tool requests execution of goal `compile` with no additional arguments
- **THEN** the server SHALL spawn `<maven-executable> compile -B` in the project directory

#### Scenario: Execute Maven goal with additional arguments
- **WHEN** a tool requests execution of goal `test` with args `["-Dtest=MyTest", "-X"]`
- **THEN** the server SHALL spawn `<maven-executable> test -B -Dtest=MyTest -X` in the project directory

#### Scenario: Environment inheritance
- **WHEN** a Maven process is spawned
- **THEN** the child process SHALL inherit the server's environment variables (including JAVA_HOME and PATH)

### Requirement: Concurrent stdout and stderr capture
The server SHALL capture stdout and stderr of the Maven child process on separate threads to prevent deadlock. Both streams SHALL be fully consumed before the process exit code is collected. The implementation SHALL use `CompletableFuture` for concurrent stream reading.

#### Scenario: Large output on both streams
- **WHEN** Maven produces output on both stdout and stderr simultaneously (e.g., compilation errors on stdout and download progress on stderr)
- **THEN** the server SHALL capture both streams completely without deadlock, regardless of output size

#### Scenario: Stream capture completes before result
- **WHEN** the Maven process terminates
- **THEN** both stdout and stderr capture threads SHALL complete, and only then SHALL the execution result be assembled

### Requirement: Execution result model
Each Maven execution SHALL return a `MavenExecutionResult` record containing:
- `exitCode` (int): process exit code (0 = success)
- `stdout` (String): complete captured standard output
- `stderr` (String): complete captured standard error
- `duration` (long): wall-clock execution time in milliseconds

#### Scenario: Successful Maven execution
- **WHEN** Maven completes with exit code 0
- **THEN** the result SHALL contain `exitCode=0`, the full stdout, full stderr, and elapsed duration in ms

#### Scenario: Failed Maven execution
- **WHEN** Maven completes with non-zero exit code (e.g., compilation failure)
- **THEN** the result SHALL contain the actual exit code, full stdout (with error details), full stderr, and elapsed duration

### Requirement: Process spawn error handling
If the Maven process cannot be started (e.g., executable not found at runtime, permission denied), the server SHALL NOT throw an unhandled exception. Instead, it SHALL return a result or error that can be translated into a meaningful MCP tool response.

#### Scenario: Maven executable deleted after startup
- **WHEN** the Maven executable was valid at startup but was removed before a tool invocation
- **THEN** the server SHALL catch the `IOException` from `ProcessBuilder.start()` and return a descriptive error (not an unhandled stack trace)

#### Scenario: Permission denied on Maven executable
- **WHEN** `ProcessBuilder.start()` fails with a permission error
- **THEN** the server SHALL catch the exception and return a descriptive error message
