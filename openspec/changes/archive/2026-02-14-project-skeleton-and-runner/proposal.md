## Why

The Maven MCP Server has a finalized SPEC.md but zero implementation code. Before any tools can be exposed to Claude Code, we need the foundational infrastructure: a working Maven project, the MCP server bootstrap (McpSyncServer on stdio), and the Maven process execution layer (ProcessBuilder wrapper with stdout/stderr capture). These are Milestones 1-2 from SPEC.md and are prerequisites for all tool implementations.

## What Changes

- New Maven project with `pom.xml` containing all dependencies (MCP SDK 0.17.2, Picocli, Logback, Jackson, JUnit 5, AssertJ)
- `MavenMcpServer` entry point with Picocli CLI (`--project <path>` argument)
- `ServerConfig` immutable configuration record with eager validation (project dir, pom.xml, Maven availability)
- `McpSyncServer` bootstrap on stdio transport — server starts and responds to MCP initialization with empty tool list
- `logback.xml` configured for stderr-only output (stdout reserved for MCP)
- `MavenRunner` — process spawning via `ProcessBuilder` with concurrent stdout/stderr capture on separate threads (`CompletableFuture`)
- `MavenDetector` — detects `./mvnw` vs system `mvn`, no caching
- `MavenExecutionResult` — record holding exit code, stdout, stderr, duration
- Unit tests for `MavenDetector` and integration test for `MavenRunner`
- `maven-shade-plugin` configuration for fat JAR

## Capabilities

### New Capabilities
- `server-bootstrap`: MCP server lifecycle — CLI argument parsing, startup validation, McpSyncServer creation on stdio transport, logging configuration
- `maven-execution`: Maven process management — executable detection (mvnw/mvn), process spawning via ProcessBuilder, concurrent stream capture, execution result model

### Modified Capabilities

_(none — greenfield project)_

## Impact

- **New files:** ~15 Java source files + pom.xml + logback.xml + test fixtures
- **Dependencies:** MCP SDK 0.17.2, Picocli 4.7.x, Logback 1.5.x, JUnit 5, AssertJ
- **APIs:** No MCP tools exposed yet — server returns empty tool list (tools come in later changes)
- **Build:** Produces `maven-mcp-server.jar` fat JAR via maven-shade-plugin
