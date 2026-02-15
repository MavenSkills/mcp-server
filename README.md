# Maven MCP Server

AI coding agents (Claude Code, Cursor, Windsurf) run Maven builds through shell commands and get back **pages of raw logs**. The agent must parse this unstructured text, burning through context window and tokens — you pay for every line of `[INFO] Downloading...` that the model reads.

**Maven MCP** is a [Model Context Protocol](https://modelcontextprotocol.io/) server that sits between the agent and Maven. It runs the build, parses the output, and returns **one structured JSON object** — just the errors, test results, and actionable information the agent needs.

## The difference

Same `maven_test` run (17 tests, all passing):

|  | Maven MCP | IntelliJ MCP | Bash (`./mvnw`) |
|---|---|---|---|
| **Characters** | ~130 | ~8 900 | ~6 200 |
| **Tokens (est.)** | ~30 | ~2 200 | ~1 600 |
| **Format** | Structured JSON | Log + TeamCity tags | Raw log |
| **Machine-parseable?** | Yes | Needs filtering | Needs filtering |

When tests fail (5 failures, Spring Boot project) the gap grows:

|  | Maven MCP | IntelliJ MCP | Bash (`./mvnw`) |
|---|---|---|---|
| **Tokens (est.)** | ~900 | ~3 400 | ~2 600 |
| **Stacktraces** | App frames only | Full | Full (repeated x5) |
| **Spring Boot logs** | Once, in first test | Full log per test | Full log |
| **Structure** | JSON `failures[]` | Raw text | Raw text |

**~50-70x fewer tokens** on success. **~3-4x fewer** on failure — with better structure.

## Setup

Requires Java 21+.

### 1. Download the JAR

```bash
mvn dependency:get -Dartifact=io.github.mavenskills:maven-mcp:1.0.1
```

This puts the JAR into your local Maven cache at:
```
~/.m2/repository/io/github/mavenskills/maven-mcp/1.0.1/maven-mcp-1.0.1.jar
```

### 2. Configure your MCP client

Add to `.mcp.json` (Claude Code) or equivalent:

```json
{
  "mcpServers": {
    "maven": {
      "command": "java",
      "args": [
        "-jar",
        "~/.m2/repository/io/github/mavenskills/maven-mcp/1.0.1/maven-mcp-1.0.1.jar",
        "--project",
        "/path/to/your/maven/project"
      ]
    }
  }
}
```

The server auto-detects `./mvnw` in the project, falling back to system `mvn`.

## Tools

| Tool | What the agent gets back |
|------|--------------------------|
| `maven_compile` | Structured errors with file, line, column |
| `maven_test` | Pass/fail summary with parsed Surefire reports, filtered stacktraces |
| `maven_clean` | Build directory cleaned confirmation |

### Smart stacktraces

Test failures include only application frames. Framework noise is collapsed:

```
com.example.MyService.process(MyService.java:42)
com.example.MyController.handle(MyController.java:18)
... 6 framework frames omitted
```

## How it works

Maven MCP spawns Maven as an external process (`./mvnw` or `mvn`), captures stdout/stderr, parses the output (compilation errors, Surefire XML reports), and returns structured JSON over MCP stdio transport. The agent never sees raw build logs.
