## 1. Response Models

- [x] 1.1 Create `CompilationError.java` record in `model` package: fields `file` (String), `line` (int), `column` (Integer, nullable), `message` (String), `severity` (String). Annotate with `@JsonInclude(NON_NULL)`.
- [x] 1.2 Create `BuildResult.java` record in `model` package: fields `status` (String), `duration` (long), `errors` (List), `warnings` (List), `summary` (Object, nullable), `failures` (List, nullable), `artifact` (Object, nullable), `output` (String, nullable). Annotate with `@JsonInclude(NON_NULL)`.
- [x] 1.3 Verify Jackson serialization: write a unit test `BuildResultTest` that serializes a BuildResult with some null fields and confirms they are omitted from JSON output.

## 2. Compilation Output Parser

- [x] 2.1 Create sample fixture files in `src/test/resources/compilation-output/`: `single-error.txt`, `multiple-errors.txt`, `warnings-only.txt`, `mixed-errors-warnings.txt`, `clean-success.txt`, `error-without-column.txt` — containing realistic Maven/javac output.
- [x] 2.2 Create `CompilationOutputParser.java` in `parser` package with method `parse(String stdout, Path projectDir) → ParseResult` where ParseResult contains separate `errors` and `warnings` lists.
- [x] 2.3 Implement error regex: `\[ERROR\]\s+(.+\.java):\[(\d+),(\d+)\]\s+(.+)` and fallback `\[ERROR\]\s+(.+\.java):\[(\d+)\]\s+(.+)` (without column).
- [x] 2.4 Implement warning regex: same patterns with `\[WARNING\]` prefix.
- [x] 2.5 Implement path normalization: convert absolute paths to project-relative using `projectDir.relativize()`. If path is not under project root, keep absolute.
- [x] 2.6 Write unit test `CompilationOutputParserTest`: test against all fixture files — single error, multiple errors, warnings-only, mixed, clean success (empty result), error without column.

## 3. Clean Tool

- [x] 3.1 Create `CleanTool.java` in `tool` package with static method `create(ServerConfig, MavenRunner) → SyncToolSpecification`. Define JSON schema with optional `args` array parameter.
- [x] 3.2 Implement handler: extract `args` from parameters map, call `MavenRunner.execute("clean", args, ...)`, build `BuildResult` with status and duration, serialize to JSON, return as `CallToolResult` with `TextContent`.
- [x] 3.3 Handle `MavenExecutionException`: catch and return `CallToolResult` with `isError(true)` and descriptive message.
- [x] 3.4 Write unit test `CleanToolTest` with mocked MavenRunner: test success scenario, test failure scenario, test args passthrough.

## 4. Compile Tool

- [x] 4.1 Create `CompileTool.java` in `tool` package with static method `create(ServerConfig, MavenRunner, ObjectMapper) → SyncToolSpecification`. Define JSON schema with optional `args` array parameter.
- [x] 4.2 Implement handler: extract `args`, call `MavenRunner.execute("compile", args, ...)`, parse stdout with `CompilationOutputParser`, build `BuildResult` with errors/warnings/status/duration/output, serialize to JSON, return as `CallToolResult`.
- [x] 4.3 Include raw output in response only when status is FAILURE.
- [x] 4.4 Write unit test `CompileToolTest` with mocked MavenRunner: test success with no warnings, test failure with errors, test success with warnings, test args passthrough.

## 5. Server Integration

- [x] 5.1 Modify `MavenMcpServer.java`: create an `ObjectMapper` configured with `@JsonInclude(NON_NULL)`, register `maven_compile` and `maven_clean` tools on the server builder via `.tool()` before `.build()`.
- [x] 5.2 Verify: run `mvn compile` — clean compilation.
- [x] 5.3 Verify: run `mvn test` — all tests pass (existing + new).
- [x] 5.4 Verify: run `mvn package` — fat JAR builds successfully.
