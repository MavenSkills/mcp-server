## Context

The project currently compiles with `<source>25</source>` / `<target>25</target>` but uses no Java 25-specific features — only records (Java 16+), text blocks (Java 15+), and var (Java 10+). When a user runs the fat JAR with an incompatible JVM, they get a raw `UnsupportedClassVersionError`. In MCP client context (Claude Code, Cursor) stderr is not displayed, so the server silently fails.

Current entry point: `MavenMcpServer` (set as `Main-Class` in shade plugin's ManifestResourceTransformer). Current compiler plugin has a single default execution. No wrapper scripts exist.

## Goals / Non-Goals

**Goals:**
- Provide a clear, actionable error message when Java version is insufficient
- Lower the minimum Java requirement to Java 21 LTS (broadest installed base)
- Keep the solution self-contained within the JAR — no wrapper scripts, no external files
- Zero impact on the happy path (Java ≥ 21) — same behavior as today

**Non-Goals:**
- Supporting Java < 21 for running the server (11 is only for the Bootstrap shim)
- Adding a general-purpose launcher or plugin system
- Detecting or validating Maven version at this stage (already handled in server-bootstrap)

## Decisions

### Decision 1: Lower compilation target to Java 21

**Choice:** Change `pom.xml` from `<source>25</source>` / `<target>25</target>` to `<source>21</source>` / `<target>21</target>`.

**Rationale:** No Java 25 features are used. Java 21 is the current LTS with the broadest install base. This is a pure compatibility win with zero trade-offs.

**Alternatives considered:**
- *Keep Java 25* — unnecessary restriction, no benefit
- *Target Java 17 (previous LTS)* — would work for current code, but Java 21 is already mainstream and gives us access to sequenced collections, pattern matching improvements, and virtual threads if needed later

### Decision 2: Bootstrap class with reflection-based delegation

**Choice:** A `Bootstrap` class compiled at Java 11 becomes the MANIFEST `Main-Class`. It checks `Runtime.version().feature()` and, if sufficient, loads `MavenMcpServer` via `Class.forName()` + reflection `invoke`.

**Rationale:** Direct import of `MavenMcpServer` would trigger class loading before version check runs, causing `UnsupportedClassVersionError` — the exact problem we're solving. Reflection defers class loading until after validation. `Runtime.version().feature()` is available since Java 10, so Java 11 target is safe.

**Alternatives considered:**
- *Wrapper shell script (bash + bat)* — adds distribution files, requires platform-specific scripts, changes MCP client config from `java -jar` to script path. Bootstrap-in-JAR is self-contained.
- *`exec-maven-plugin`* — requires cloned repo + built project, adds Maven as runtime dependency for the MCP server. Not a distribution mechanism.
- *Multi-release JAR (MRJAR)* — overkill for a single entry-point shim, adds build complexity for no benefit. MRJAR is designed for API-level differences across versions, not entry-point routing.

### Decision 3: Separate source root with dual compiler executions

**Choice:** Place `Bootstrap.java` in `src/main/java-bootstrap/` and add a second `maven-compiler-plugin` execution with `<source>11</source>` / `<target>11</target>` scoped to that source root.

**Rationale:** Keeps Java 11 code physically separated from Java 21 code. The compiler plugin supports multiple executions with different configurations. Both source roots compile to the same `target/classes/` output, so the shade plugin packages everything into one fat JAR.

**Alternatives considered:**
- *Separate Maven module* — excessive for a single file, complicates the build for a simple project
- *Compile everything at Java 11 and use `--enable-preview`* — defeats the purpose of using Java 21 features cleanly
- *Single source root with per-file compiler config* — Maven compiler plugin doesn't support per-file release targets cleanly; separate source roots are the idiomatic approach

### Decision 4: Bootstrap compiled at Java 11 (not 8)

**Choice:** Target Java 11 for the Bootstrap class.

**Rationale:** `Runtime.version()` was introduced in Java 9. Java 11 is the oldest LTS that supports it. Targeting Java 8 would require parsing `java.version` system property manually — more fragile and unnecessary since Java 8 is EOL.

### Decision 5: Exit code and error output

**Choice:** On version mismatch, Bootstrap prints three lines to stderr and exits with code 1:
```
maven-mcp requires Java 21+
Detected: Java <version>
Install Java 21+: https://adoptium.net/
```

**Rationale:** Three lines: what's required, what was detected, how to fix it. Exit code 1 is consistent with existing server-bootstrap validation failures. Adoptium is the primary open-source JDK distribution.

## Risks / Trade-offs

| Risk | Severity | Mitigation |
|------|----------|------------|
| Dual compiler execution ordering — bootstrap execution must not interfere with default execution | Low | The `compile-bootstrap` execution runs in `compile` phase alongside default. Both write to `target/classes/`. Package namespaces don't overlap with main code except the shared `io.github.mavenmcp` package. Test with `mvn clean compile` to verify. |
| Reflection hides compile-time errors in the delegation path | Low | The delegation is a single `Class.forName` + `getMethod("main")` call. If `MavenMcpServer` is renamed or `main` signature changes, it fails at runtime. Mitigated by integration test that boots the JAR. |
| Users on Java 11-20 get a clear error but might expect the server to work | Low | The error message explicitly states "requires Java 21+" — no ambiguity. |
| Bootstrap adds ~1ms startup overhead on happy path | Negligible | Single reflection call, no class scanning. Unmeasurable in practice. |
