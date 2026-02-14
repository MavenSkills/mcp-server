# Server Bootstrap

### Requirement: CLI entry point with project path argument
The server SHALL accept a `--project` CLI argument specifying the absolute path to the Maven project directory. The argument SHALL be parsed using Picocli. The `--project` argument SHALL be required — the server SHALL exit with code 1 and a human-readable error message if it is not provided.

#### Scenario: Server started with valid project path
- **WHEN** the server is started with `--project /path/to/valid-maven-project`
- **THEN** the server SHALL initialize successfully and begin listening on stdio

#### Scenario: Server started without --project argument
- **WHEN** the server is started without the `--project` argument
- **THEN** the server SHALL print a usage/help message to stderr and exit with code 1

#### Scenario: Help flag
- **WHEN** the server is started with `--help`
- **THEN** the server SHALL print usage information to stderr and exit with code 0

### Requirement: Eager startup validation
The server SHALL validate the following conditions at startup, before accepting any MCP tool calls:
1. The `--project` path exists and is a directory
2. A `pom.xml` file exists in the project directory
3. A Maven executable is available (mvnw or mvn)

If any validation fails, the server SHALL exit with code 1 and a descriptive error message to stderr.

#### Scenario: Project directory does not exist
- **WHEN** the server is started with `--project /nonexistent/path`
- **THEN** the server SHALL exit with code 1 and print "Project directory does not exist: /nonexistent/path" to stderr

#### Scenario: Project directory has no pom.xml
- **WHEN** the server is started with `--project /path/to/dir` where the directory exists but contains no `pom.xml`
- **THEN** the server SHALL exit with code 1 and print "No pom.xml found in project directory: /path/to/dir" to stderr

#### Scenario: No Maven executable available
- **WHEN** the server is started with a valid project directory but neither `./mvnw` nor `mvn` is available
- **THEN** the server SHALL exit with code 1 and print "Maven not found. Install Maven or add mvnw to your project." to stderr

### Requirement: MCP server initialization on stdio
The server SHALL create a `McpSyncServer` using `StdioServerTransportProvider` and register itself with server info name `"maven-mcp"` and version `"1.0.0"`. The server SHALL declare the `tools` capability. After initialization, the server SHALL listen on stdin for MCP JSON-RPC messages and respond on stdout.

#### Scenario: MCP client connects and lists tools
- **WHEN** an MCP client connects and sends an `initialize` request followed by `tools/list`
- **THEN** the server SHALL respond with server info `{"name": "maven-mcp", "version": "1.0.0"}` and an empty tool list (tools are registered in later changes)

#### Scenario: Server keeps running until stdin closes
- **WHEN** the MCP client keeps the stdin stream open
- **THEN** the server SHALL remain running and responsive to further JSON-RPC messages

### Requirement: Logging exclusively to stderr
All server logging SHALL be output to stderr only. The server SHALL NOT write any non-MCP content to stdout. Logging SHALL use SLF4J API with Logback Classic as the implementation. The default log level SHALL be INFO.

#### Scenario: Startup logging does not pollute stdout
- **WHEN** the server starts and logs startup messages (project path, Maven detected, server version)
- **THEN** all log messages SHALL appear on stderr and stdout SHALL contain only valid MCP JSON-RPC messages

#### Scenario: Log format
- **WHEN** a log event occurs
- **THEN** the log line SHALL follow the format `[TIMESTAMP] [LEVEL] [LOGGER] - MESSAGE` on stderr

### Requirement: Immutable server configuration
The server SHALL store validated configuration in an immutable `ServerConfig` record containing at minimum: `projectDir` (Path) and `mavenExecutable` (Path). The record SHALL be created after successful validation and passed to components that need it.

#### Scenario: Config created after validation
- **WHEN** all startup validations pass
- **THEN** a `ServerConfig` record SHALL be created with the validated project directory and detected Maven executable path
