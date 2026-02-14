## Context

The Maven MCP Server has a working skeleton (Milestone 1-2): McpSyncServer on stdio, MavenRunner with ProcessBuilder, MavenDetector. The server starts and responds to MCP initialization but exposes zero tools. This change adds the first two tools (`maven_compile`, `maven_clean`) and the compilation output parser — making the server functionally useful for the first time.

Existing code: `MavenMcpServer.java` (bootstrap), `MavenRunner.java` (process execution), `ServerConfig.java`, `MavenDetector.java`.

## Goals / Non-Goals

**Goals:**
- `CompilationOutputParser` — regex-based parser for javac error/warning output
- `BuildResult` model with Jackson serialization (null-field omission)
- `CompilationError` model
- `maven_compile` tool registered on McpSyncServer
- `maven_clean` tool registered on McpSyncServer
- Unit tests for parser with fixture files
- Unit tests for tool handlers with mocked MavenRunner

**Non-Goals:**
- Surefire/test parsing (Milestone 4)
- `maven_test` and `maven_package` tools (Milestones 4-5)
- Timeout support (Phase 2)

## Decisions

### D1: Tool registration — inline in MavenMcpServer vs separate registration method

**Decision:** Create tool specifications in each tool handler class as a static factory method, then register them in `MavenMcpServer.call()` before `build()`.

**Alternatives considered:**
- Inline everything in MavenMcpServer — cluttered main class
- Auto-discovery with classpath scanning — overkill for 4 tools

**Rationale:** Each tool class owns its JSON schema, description, and handler logic. MavenMcpServer just wires them together. Clean separation.

### D2: Tool handler pattern — static method returning SyncToolSpecification

**Decision:** Each tool class (e.g., `CompileTool`) has a static method `create(ServerConfig, MavenRunner)` that returns a `McpServerFeatures.SyncToolSpecification`. The BiFunction handler is a lambda that closes over the config and runner.

**Rationale:** Keeps tool classes self-contained. No need for a tool interface or abstract base class — the MCP SDK's `SyncToolSpecification` record is the abstraction.

### D3: BuildResult serialization — Jackson with @JsonInclude(NON_NULL)

**Decision:** Annotate `BuildResult` with `@JsonInclude(JsonInclude.Include.NON_NULL)` so that null fields (e.g., `output` on success, `summary`/`failures` for non-test tools) are omitted from JSON. Serialize using the ObjectMapper from JacksonMcpJsonMapper.

**Alternatives considered:**
- Manual JSON construction with StringBuilder — error-prone, no type safety
- Separate response classes per tool — unnecessary duplication, SPEC.md defines a single BuildResult

**Rationale:** Jackson is already a transitive dependency. Annotation-driven serialization is clean, and null-omission keeps responses compact for the AI agent.

### D4: Compilation parser — two-pass regex (errors then warnings) vs single-pass

**Decision:** Single pass through stdout lines. For each line, try the error regex first, then the warning regex. Collect results in two separate lists.

**Rationale:** Simple, single iteration. Output order is preserved. No need for two passes.

### D5: Regex patterns — strict vs lenient

**Decision:** Use relatively lenient patterns:
- Error: `\[ERROR\]\s+(.+\.java):\[(\d+),(\d+)\]\s+(.+)` (with column)
- Error alt: `\[ERROR\]\s+(.+\.java):\[(\d+)\]\s+(.+)` (without column)
- Warning: same patterns with `\[WARNING\]`

**Rationale:** javac output format is stable across Maven Compiler Plugin versions. Two patterns per severity handle both `[line,col]` and `[line]` formats.

### D6: Tool handler error handling — MavenExecutionException → CallToolResult with isError

**Decision:** If `MavenRunner.execute()` throws `MavenExecutionException` (IOException from ProcessBuilder), the tool handler catches it and returns a `CallToolResult` with `isError: true` and a descriptive message. Normal Maven failures (non-zero exit code) are NOT errors — they're returned as `status: "FAILURE"` in BuildResult with `isError: false`.

**Rationale:** MCP distinguishes between tool execution errors (infrastructure problems) and tool results that indicate failure (application-level). A compilation failure is a valid result, not an error.

## Risks / Trade-offs

**[Risk] Regex doesn't match all javac error formats** → Mitigated by fixture-based tests with real Maven output. Easy to add new patterns later without changing the tool handler.

**[Risk] ObjectMapper instance sharing** → MCP SDK owns an ObjectMapper internally. We create our own for BuildResult serialization. No conflict — they're independent instances.

**[Trade-off] BuildResult is a shared model for all tools** → Some fields are irrelevant for some tools (e.g., `summary`/`failures` for compile). This is intentional per SPEC.md — null fields are omitted from JSON, so the response is clean.
