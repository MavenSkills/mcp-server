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
