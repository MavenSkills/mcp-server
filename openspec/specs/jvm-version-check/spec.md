# JVM Version Check

### Requirement: JVM version validation before application loading
The server JAR SHALL include a `Bootstrap` class compiled at Java 11 bytecode level. The `Bootstrap` class SHALL be the MANIFEST `Main-Class` entry point. On startup, `Bootstrap` SHALL check the running JVM version using `Runtime.version().feature()` and compare it against the minimum required version (Java 21). If the version is sufficient, `Bootstrap` SHALL load `MavenMcpServer` via `Class.forName()` and invoke its `main` method via reflection. If the version is insufficient, `Bootstrap` SHALL print a human-readable error to stderr and exit with code 1.

#### Scenario: JVM version is sufficient (Java 21+)
- **WHEN** the JAR is executed with Java 21 or higher
- **THEN** `Bootstrap` SHALL load `MavenMcpServer` via reflection and invoke its `main` method, passing through all CLI arguments

#### Scenario: JVM version is insufficient (Java < 21)
- **WHEN** the JAR is executed with a Java version lower than 21
- **THEN** `Bootstrap` SHALL print the following three lines to stderr and exit with code 1:
  - `maven-mcp requires Java 21+`
  - `Detected: Java <version>` (where `<version>` is the detected feature version)
  - `Install Java 21+: https://adoptium.net/`

#### Scenario: Reflection delegation fails
- **WHEN** the JVM version is sufficient but `MavenMcpServer` class cannot be loaded or its `main` method cannot be invoked
- **THEN** `Bootstrap` SHALL print `Failed to start maven-mcp: <exception message>` to stderr and exit with code 1

### Requirement: Bootstrap compiled at Java 11 bytecode level
The `Bootstrap` class SHALL be compiled with `<source>11</source>` / `<target>11</target>` to ensure it can be loaded by any JVM from Java 11 onward. It SHALL reside in a separate source root (`src/main/java-bootstrap/`) from the main application code. The `Bootstrap` class SHALL NOT import or directly reference any class compiled at Java 21 level — all delegation MUST use reflection.

#### Scenario: JAR executed with Java 11
- **WHEN** the JAR is executed with Java 11
- **THEN** the `Bootstrap` class SHALL load and execute successfully (printing the version error), rather than throwing `UnsupportedClassVersionError`

#### Scenario: JAR executed with Java 17
- **WHEN** the JAR is executed with Java 17
- **THEN** the `Bootstrap` class SHALL load and execute successfully (printing the version error), rather than throwing `UnsupportedClassVersionError`
