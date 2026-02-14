# Maven MCP Server

MCP server that wraps Maven CLI operations and returns structured, parsed output to AI agents via stdio transport.

## Tools

| Tool | Description |
|------|-------------|
| `maven_compile` | Compile project. Returns structured errors with file, line, column. |
| `maven_test` | Run tests. Returns pass/fail summary with parsed Surefire reports. |
| `maven_clean` | Clean the build directory (`target/`). |

## Build

Requires Java 25+ and Maven.

```bash
mvn package
```

Produces `target/maven-mcp.jar` (fat JAR with all dependencies).

## Configuration

### Claude Code (`.mcp.json`)

```json
{
  "mcpServers": {
    "maven-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/maven-mcp.jar",
        "--project",
        "/path/to/your/maven/project"
      ]
    }
  }
}
```

The `--project` argument points to the Maven project directory (must contain `pom.xml`).

The server auto-detects `./mvnw` wrapper in the project directory, falling back to system `mvn`.

## Why MCP instead of raw Maven?

LLM agents pay for every token of context they consume. Maven MCP returns a single structured JSON line instead of pages of build logs — **~70x fewer tokens** than IntelliJ MCP and **~50x fewer** than raw `./mvnw`.

Comparison for the same `maven_test` run (17 tests, all passing):

|  | Maven MCP | IntelliJ MCP | Bash (`./mvnw`) |
|---|---|---|---|
| **Characters** | ~130 | ~8 900 | ~6 200 |
| **Tokens (est.)** | ~30 | ~2 200 | ~1 600 |
| **Lines** | 1 | ~85 | ~75 |
| **Format** | Structured JSON | Log + TeamCity tags | Log + Surefire summary |
| **Machine-parseable?** | Immediately | Needs filtering | Needs filtering |

The difference grows when tests fail (5 failing tests, Spring Boot project):

|  | Maven MCP | IntelliJ MCP | Bash (`./mvnw`) |
|---|---|---|---|
| **Characters** | ~3 500 | ~13 500 | ~10 500 |
| **Tokens (est.)** | ~900 | ~3 400 | ~2 600 |
| **Stacktrace** | Filtered (app frames only) | Full (in TeamCity tags) | Full (repeated x5) |
| **Spring Boot logs** | Only in 1st test `testOutput` | Full log | Full log |
| **Structure** | JSON with `failures[]` | Raw text | Raw text |
| **Stacktrace duplication** | Once in `failures`, once in `output` | Once in `##teamcity` per test | 2x per test (inline + summary) |

Maven MCP intelligently filters stacktraces to application frames (`... 6 framework frames omitted`), structures failures in JSON with `testClass`, `testMethod`, `message`, and avoids duplicating the full build log — **~4x fewer tokens** than IntelliJ and **~3x fewer** than raw Maven on failure scenarios.
