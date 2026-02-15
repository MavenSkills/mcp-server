## Why

When a user runs `java -jar maven-mcp.jar` with a Java version lower than required, the JVM throws `UnsupportedClassVersionError` and exits immediately. In a terminal this is visible, but in MCP context (Claude Code, Cursor, etc.) stderr is not shown to the user — the server simply "doesn't work" with no explanation. A human-readable version check before class loading solves this. Additionally, the project currently targets Java 25 but uses no features beyond Java 21 (records, text blocks, var), so lowering the target to Java 21 LTS broadens compatibility with no trade-offs.

## What Changes

- **Lower compilation target from Java 25 to Java 21** — `pom.xml` source/target change. Java 21 is the current LTS and supports all features used in the codebase. **BREAKING**: users who somehow depended on Java 25 class file version (unlikely).
- **Add Bootstrap entry point compiled at Java 11** — a new `Bootstrap` class in a separate source root (`src/main/java-bootstrap/`), compiled with `target=11`, becomes the JAR's `Main-Class`. It checks `Runtime.version().feature() >= 21` before loading `MavenMcpServer` via reflection.
- **Change MANIFEST Main-Class** — from `MavenMcpServer` to `Bootstrap`. The Bootstrap class delegates to the real entry point after version validation passes.
- **Dual-compilation in maven-compiler-plugin** — two executions: one for `src/main/java-bootstrap/` at target=11, one for `src/main/java/` at target=21.

## Capabilities

### New Capabilities
- `jvm-version-check`: Bootstrap class that validates the JVM version (≥ 21) at startup before loading the main application. Provides a clear error message with install instructions when the version is insufficient. Uses reflection to avoid premature class loading of Java 21+ classes.

### Modified Capabilities
- `server-bootstrap`: The JAR entry point changes from `MavenMcpServer` to `Bootstrap`. The startup flow becomes: Bootstrap (Java 11) → version check → reflection load → `MavenMcpServer.main()` (Java 21). MANIFEST `Main-Class` attribute updates accordingly.

## Impact

- **Build config**: `pom.xml` — compiler plugin gets dual execution config; shade plugin `mainClass` changes to `Bootstrap`
- **Source layout**: new source root `src/main/java-bootstrap/` with single `Bootstrap.java` file
- **Runtime behavior**: users with Java < 21 get a clear error message instead of `UnsupportedClassVersionError`
- **CI**: GitHub Actions workflow already uses Java 21 (Temurin) — no change needed
- **External API**: no MCP protocol changes, no tool changes
