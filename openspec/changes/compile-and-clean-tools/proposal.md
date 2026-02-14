## Why

The MCP server has a working bootstrap and Maven runner but exposes zero tools — it's useless to Claude Code. This change adds the first two functional MCP tools (`maven_compile`, `maven_clean`) and the compilation output parser that transforms raw javac error messages into structured data with file/line/column. After this, the agent can compile projects and immediately locate errors.

## What Changes

- New `CompilationOutputParser` that extracts errors and warnings from Maven/javac stdout using regex patterns
- Paths in compilation errors normalized to relative (relative to project root)
- New `BuildResult` top-level response model with Jackson serialization (null fields omitted)
- New `CompilationError` model (file, line, column, message, severity)
- New `maven_compile` MCP tool — invokes `mvn compile -B`, parses output, returns structured errors/warnings
- New `maven_clean` MCP tool — invokes `mvn clean -B`, returns success/failure status
- Both tools accept optional `args` parameter for extra Maven flags
- Raw Maven output included in response only on FAILURE
- Tools registered on the `McpSyncServer` at startup

## Capabilities

### New Capabilities
- `compilation-parsing`: Regex-based parser for Maven/javac compilation output — extracts errors and warnings with file path, line, column, message, and severity
- `compile-tool`: MCP tool `maven_compile` — compiles a Maven project and returns structured compilation results
- `clean-tool`: MCP tool `maven_clean` — cleans the Maven project build directory

### Modified Capabilities
_(none — existing server-bootstrap and maven-execution specs are unchanged)_

## Impact

- **New files:** ~8 Java source files (parser, models, tool handlers) + test fixtures
- **Modified files:** `MavenMcpServer.java` (register tools on the server)
- **MCP API:** Server now reports 2 tools in `tools/list` response
- **Dependencies:** No new dependencies (Jackson already transitive via MCP SDK)
