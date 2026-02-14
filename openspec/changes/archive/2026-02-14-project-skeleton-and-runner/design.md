## Context

This is the first implementation change for the Maven MCP Server — a greenfield Java project. SPEC.md defines the full MVP (Milestones 1-6). This change covers Milestones 1-2: the project skeleton (build config, MCP server bootstrap, CLI, logging) and the Maven execution layer (process spawning, stream capture, executable detection).

No source code exists yet. The project will be built with Maven (dogfooding) and use the official MCP Java SDK 0.17.2 for stdio transport.

**Constraints from SPEC.md:**
- Java 25+, Maven build
- McpSyncServer with StdioServerTransportProvider
- stdout reserved exclusively for MCP JSON-RPC — all logging to stderr
- Picocli for CLI argument parsing
- ProcessBuilder for Maven child process management

## Goals / Non-Goals

**Goals:**
- Working Maven project that compiles and packages into a fat JAR
- MCP server that starts, accepts connections on stdio, and responds to `initialize` and `tools/list` (empty list)
- Maven process runner that can spawn `mvn <goal>` and capture stdout/stderr without deadlock
- Maven executable detection (mvnw priority, mvn fallback)
- Fail-fast startup validation (project dir, pom.xml, Maven availability)
- Logging infrastructure configured for stderr-only output
- Unit tests for detector, integration test for runner

**Non-Goals:**
- No MCP tools registered (compile/test/package/clean come in later changes)
- No output parsing (compilation errors, Surefire XML — later changes)
- No timeout support (Phase 2 per SPEC.md)
- No Windows support (mvnw.cmd detection — Phase 2)

## Decisions

### D1: Project structure — flat package with sub-packages

**Decision:** Use `io.github.mavenmcp` as root package with sub-packages: `config`, `maven`, `tool`, `parser`, `model`.

**Alternatives considered:**
- Single flat package — too messy once we add tools and parsers
- Deeper nesting (e.g., `io.github.mavenmcp.server.mcp.transport`) — over-engineered for this project size

**Rationale:** Matches SPEC.md Section 10. Clean separation of concerns without unnecessary depth. Each sub-package maps to a layer in the architecture.

### D2: Stream capture — CompletableFuture with thread pool

**Decision:** Use `CompletableFuture.supplyAsync()` to read stdout and stderr on separate threads from the common ForkJoinPool. Join both futures after process completes.

**Alternatives considered:**
- Manual `Thread` creation — more boilerplate, same result
- `ProcessBuilder.redirectErrorStream(true)` — merges streams, loses ability to separate Maven output from download logs
- `Process.inputReader()` (Java 17+) — convenient but still needs separate threads

**Rationale:** CompletableFuture is idiomatic modern Java, integrates cleanly with the JDK thread pool, and makes error handling (exceptions during read) straightforward.

### D3: Stream reading strategy — read entire output into String

**Decision:** Read the complete stdout/stderr into `String` using `BufferedReader.lines().collect(joining("\n"))`.

**Alternatives considered:**
- Line-by-line streaming with callbacks — more complex, premature optimization for MVP
- Byte-array capture with `InputStream.readAllBytes()` — loses line-by-line processing option

**Rationale:** Maven output for single-module projects is typically < 1MB. String capture is simple and sufficient. Parsers (in later changes) receive the complete String and process it. If memory becomes an issue with very large outputs, we can switch to streaming later without changing the MavenRunner interface (the `MavenExecutionResult.stdout` field stays a String).

### D4: Maven detection — filesystem check at startup, re-verify at invocation

**Decision:** At startup, detect and validate Maven executable (for fail-fast). Before each invocation, re-check that the file still exists (1ms filesystem call). Do not cache.

**Alternatives considered:**
- Cache at startup, never re-check — risk of stale reference if mvnw is added/removed during session
- Re-detect full logic each time (including PATH search) — unnecessary overhead for the fallback case

**Rationale:** As decided in SPEC.md (D-09). `File.exists()` is negligible cost. Handles the edge case where mvnw is added to the project during a session.

### D5: Fat JAR — maven-shade-plugin

**Decision:** Use `maven-shade-plugin` to produce a single executable JAR with all dependencies bundled.

**Alternatives considered:**
- `maven-assembly-plugin` — works but shade is more standard for fat JARs
- `jlink` / custom runtime image — overkill, requires JDK on target anyway
- Spring Boot plugin — we don't use Spring Boot

**Rationale:** Standard approach. Single `java -jar` command to run. Matches SPEC.md Section 12 (Milestone 6) and Claude Code MCP config pattern.

### D6: Picocli integration — @Command annotation on main class

**Decision:** Annotate `MavenMcpServer` with Picocli `@Command`. The class implements `Callable<Integer>`, where `call()` performs validation, bootstraps the MCP server, and returns exit code.

**Alternatives considered:**
- Separate CLI class delegating to server — extra indirection for one argument
- Picocli `CommandLine.execute()` with subcommands — overkill, no subcommands needed

**Rationale:** Simplest Picocli pattern. The main class IS the command. One `@Option` for `--project`, auto-generated `--help` and `--version`.

### D7: pom.xml — Java 25 with preview features disabled

**Decision:** Set `maven.compiler.source` and `maven.compiler.target` to `25`. Do not enable preview features unless specifically needed.

**Alternatives considered:**
- Enable `--enable-preview` — unnecessary complexity, no preview features needed for this project
- Target Java 21 (LTS) — REQUIREMENTS.md specifies Java 25+

**Rationale:** Follow the constraint. Java 25 provides everything we need (records, sealed classes, pattern matching) without preview flags.

## Risks / Trade-offs

**[Risk] MCP SDK 0.17.2 API instability** → Pin exact version. Encapsulate all SDK usage in MavenMcpServer bootstrap. If API changes, only one file needs updating.

**[Risk] stdout pollution from dependencies** → Logback stderr-only config mitigates our code. Third-party libraries that use `System.out.println` could still break MCP. Mitigation: test that no non-JSON appears on stdout during server lifecycle.

**[Risk] ProcessBuilder deadlock on large output** → Mitigated by design (D2): CompletableFuture-based concurrent stream capture. Integration test verifies with real Maven output.

**[Trade-off] Full String capture vs streaming** → We capture entire stdout/stderr into Strings (D3). This is simple but uses O(output_size) memory. For single-module Maven builds this is fine (< 1MB typical). If this becomes a problem, we can switch to streaming without changing the public API.

**[Trade-off] No tools in this change** → The server starts but does nothing useful. This is intentional — we validate the foundation before adding tools. The risk is that integration issues only surface when tools are added. Mitigation: integration test runs `mvn --version` through the full MavenRunner pipeline.

## Open Questions

_(none — all architectural decisions resolved in SPEC.md and during discovery)_
