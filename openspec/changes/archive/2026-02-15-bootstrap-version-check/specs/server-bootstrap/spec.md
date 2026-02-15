## MODIFIED Requirements

### Requirement: CLI entry point with project path argument
The server JAR's MANIFEST `Main-Class` SHALL be `io.github.mavenmcp.Bootstrap`. The `Bootstrap` class SHALL validate the JVM version and then delegate to `MavenMcpServer.main()` via reflection. `MavenMcpServer` SHALL accept a `--project` CLI argument specifying the absolute path to the Maven project directory. The argument SHALL be parsed using Picocli. The `--project` argument SHALL be required — the server SHALL exit with code 1 and a human-readable error message if it is not provided.

#### Scenario: Server started with valid project path
- **WHEN** the server is started with `--project /path/to/valid-maven-project`
- **THEN** `Bootstrap` SHALL delegate to `MavenMcpServer`, which SHALL initialize successfully and begin listening on stdio

#### Scenario: Server started without --project argument
- **WHEN** the server is started without the `--project` argument
- **THEN** the server SHALL print a usage/help message to stderr and exit with code 1

#### Scenario: Help flag
- **WHEN** the server is started with `--help`
- **THEN** the server SHALL print usage information to stderr and exit with code 0
