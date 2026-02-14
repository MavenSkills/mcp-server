## 1. Project Setup

- [x] 1.1 Create `pom.xml` with project coordinates (`io.github.mavenmcp:maven-mcp-server:1.0.0-SNAPSHOT`), Java 25 compiler settings, and dependency declarations: MCP SDK 0.17.2, Picocli 4.7.x, Logback Classic 1.5.x, JUnit Jupiter 5.11.x, AssertJ 3.26.x
- [x] 1.2 Configure `maven-shade-plugin` in `pom.xml` to produce fat JAR with `MavenMcpServer` as main class
- [x] 1.3 Create `src/main/resources/logback.xml` with stderr-only appender (ConsoleAppender targeting System.err), INFO default level, format `[%d] [%level] [%logger] - %msg%n`
- [x] 1.4 Create base package directory structure: `io.github.mavenmcp` with sub-packages `config`, `maven`, `tool`, `parser`, `model`

## 2. Configuration & CLI

- [x] 2.1 Create `ServerConfig.java` record in `config` package with fields: `projectDir` (Path), `mavenExecutable` (Path)
- [x] 2.2 Create `MavenMcpServer.java` in root package as Picocli `@Command` implementing `Callable<Integer>` with `@Option(names = "--project", required = true)` for project path
- [x] 2.3 Implement startup validation in `MavenMcpServer.call()`: check project directory exists, pom.xml present, Maven executable available — exit with code 1 and descriptive stderr message on failure
- [x] 2.4 Write unit test `ServerConfigTest` verifying: valid config creation, rejection of non-existent path, rejection of directory without pom.xml

## 3. Maven Executable Detection

- [x] 3.1 Create `MavenNotFoundException.java` (unchecked exception) in `maven` package
- [x] 3.2 Create `MavenDetector.java` in `maven` package with method `detect(Path projectDir) → Path`: check `./mvnw` exists and is executable → fallback to `mvn` on PATH via `which mvn` / checking common locations → throw `MavenNotFoundException` if neither found
- [x] 3.3 Write unit test `MavenDetectorTest`: scenario with mvnw present (temp dir with executable mvnw), scenario with only system mvn, scenario with neither (expects MavenNotFoundException), scenario with non-executable mvnw (fallback to system mvn)

## 4. Maven Process Execution

- [x] 4.1 Create `MavenExecutionResult.java` record in `maven` package with fields: `exitCode` (int), `stdout` (String), `stderr` (String), `duration` (long, ms)
- [x] 4.2 Create `MavenRunner.java` in `maven` package with method `execute(String goal, List<String> extraArgs, Path mavenExecutable, Path projectDir) → MavenExecutionResult`
- [x] 4.3 Implement ProcessBuilder setup in `MavenRunner`: set working directory, build command `[mavenExe, goal, "-B", ...extraArgs]`, inherit environment, redirect nothing (capture both streams)
- [x] 4.4 Implement concurrent stdout/stderr capture using `CompletableFuture.supplyAsync()` — one future per stream, `BufferedReader.lines().collect(joining("\n"))`, join both before collecting exit code
- [x] 4.5 Implement duration measurement: `System.currentTimeMillis()` before `process.start()` and after `process.waitFor()`
- [x] 4.6 Implement error handling: catch `IOException` from `ProcessBuilder.start()` and wrap in descriptive error (not unhandled exception)
- [x] 4.7 Write integration test `MavenRunnerTest`: execute `mvn --version` (or `mvn validate` on a minimal test project in test resources), verify exitCode=0, stdout contains Maven version string, duration > 0

## 5. MCP Server Bootstrap

- [x] 5.1 Add MCP server initialization in `MavenMcpServer.call()`: create `StdioServerTransportProvider(new ObjectMapper())`, build `McpSyncServer` via `McpServer.sync(transport).serverInfo("maven-mcp", "1.0.0").capabilities(tools=true, logging).build()`
- [x] 5.2 Store `ServerConfig` and `MavenRunner` as fields accessible to future tool handlers (prepared for later changes)
- [x] 5.3 Add startup log messages: project path, detected Maven executable, server version — all to stderr via SLF4J
- [x] 5.4 Verify manually: start server with `java -jar ... --project <path>`, confirm it initializes and responds to MCP `initialize` handshake without errors

## 6. Verification & Packaging

- [x] 6.1 Run `mvn compile` — verify clean compilation with zero warnings
- [x] 6.2 Run `mvn test` — verify all unit and integration tests pass
- [x] 6.3 Run `mvn package` — verify fat JAR is produced in `target/`
- [x] 6.4 Run `java -jar target/maven-mcp-server.jar --project .` — verify server starts (uses own project as target), logs to stderr, and does not output anything on stdout except MCP messages
- [x] 6.5 Run `java -jar target/maven-mcp-server.jar` (no args) — verify it prints usage to stderr and exits with code 1
