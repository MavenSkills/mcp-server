# Maven MCP Server

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mavenskills/maven-mcp)](https://central.sonatype.com/artifact/io.github.mavenskills/maven-mcp)
[![CI](https://github.com/MavenSkills/mcp-server/actions/workflows/ci.yml/badge.svg)](https://github.com/MavenSkills/mcp-server/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

AI coding agents (Claude Code, Cursor, Windsurf) run Maven builds through shell commands and get back **pages of raw logs**. The agent must parse this unstructured text, burning through context window and tokens — you pay for every line of `[INFO] Downloading...` that the model reads.

**Maven MCP** is a [Model Context Protocol](https://modelcontextprotocol.io/) server that sits between the agent and Maven. It runs the build, parses the output, and returns **concise Markdown** — just the errors, test results, and actionable information the agent needs.

Tested with Claude Code. Compatible with any MCP client.

## Token savings

Same project, same tests — different token cost for the agent:

|  | Maven MCP | IntelliJ MCP | Bash (`./mvnw`) |
|---|---|---|---|
| **17 tests passing** | ~30 tokens | ~2 200 tokens | ~1 600 tokens |
| **5 failures (Spring Boot)** | ~900 tokens | ~3 400 tokens | ~2 600 tokens |

**~50x fewer tokens** on success. **~3x fewer** on failure — as concise Markdown instead of raw logs.

### At scale: 205 errors, one root cause

A Redis port conflict causes 205 tests to error out. Raw Maven output: **5.4 MB** — mostly repeated Spring context dumps and framework stack traces.

|  | Maven MCP | Bash (`./mvnw test`) |
|---|---|---|
| **Size** | ~9 KB | ~5.4 MB |
| **Failures shown** | 2 (deduplicated) | 205 (all identical) |
| **Root cause** | `bind: address already in use` | Buried in logs |
| **Reduction** | **~600x** | — |

Exception headers are truncated, framework frames collapsed, and identical failures grouped by root cause. The agent reads 9 KB instead of 5 MB.

## Setup

Requires Java 21+.

### 1. Download the JAR

```bash
mvn dependency:get -Dartifact=io.github.mavenskills:maven-mcp:1.1.0
```

This puts the JAR into your local Maven cache at:
```
~/.m2/repository/io/github/mavenskills/maven-mcp/1.1.0/maven-mcp-1.1.0.jar
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
        "~/.m2/repository/io/github/mavenskills/maven-mcp/1.1.0/maven-mcp-1.1.0.jar"
      ]
    }
  }
}
```

The server defaults to the current working directory. MCP clients like Claude Code set the working directory to the project root, so no extra configuration is needed.

The server auto-detects `./mvnw` in the project, falling back to system `mvn`.

## Tools

| Tool | What the agent gets back |
|------|--------------------------|
| `maven_compile` | Structured errors with file, line, column |
| `maven_test` | Pass/fail summary, parsed Surefire reports, filtered stacktraces |
| `maven_clean` | Build directory cleaned confirmation |

### `maven_test` in detail

The test tool does more than run `mvn test`:

- **`testOnly` mode** (default) — runs `surefire:test` directly, skipping the full Maven lifecycle. If sources changed since last compile, auto-recompiles before running. Saves 2-5s per iteration.
- **Smart stacktraces** — only application frames are shown, framework noise is collapsed:
  ```
  com.example.MyService.process(MyService.java:42)
  com.example.MyController.handle(MyController.java:18)
  ... 6 framework frames omitted
  ```
- **Failure deduplication** — 205 identical failures become 2 entries. Same root cause = one entry.
- **Test filtering** — `testFilter: "MyTest"`, `testFilter: "MyTest#method"`, or `testFilter: "MyTest,OtherTest"`.
- **Configurable output** — `stackTraceLines` (default 50), `appPackage` (auto-derived from pom.xml groupId), `testOutputLimit` per test.

## How it works

Maven MCP spawns Maven as an external process (`./mvnw` or `mvn`), captures stdout/stderr, parses the output (compilation errors, Surefire XML reports), and returns concise Markdown over MCP stdio transport. The agent never sees raw build logs.

## License

Apache-2.0
