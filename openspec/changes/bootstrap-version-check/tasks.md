## 1. Lower compilation target to Java 21

- [x] 1.1 Change `maven.compiler.source` and `maven.compiler.target` properties from `25` to `21` in `pom.xml`
- [x] 1.2 Update `maven-compiler-plugin` default execution `<source>` and `<target>` from `25` to `21`
- [x] 1.3 Verify project compiles cleanly with `mvn clean compile` and all existing tests pass

## 2. Add Bootstrap source root and dual compilation

- [x] 2.1 Create directory `src/main/java-bootstrap/io/github/mavenmcp/`
- [x] 2.2 Add second `maven-compiler-plugin` execution (`compile-bootstrap`) targeting Java 11, with `compileSourceRoots` set to `src/main/java-bootstrap/` and `source`/`target` set to `11`
- [x] 2.3 Modify the default compiler execution to exclude `src/main/java-bootstrap/` (use `excludes` or explicit `compileSourceRoots` for `src/main/java/`)
- [x] 2.4 Verify `mvn clean compile` produces classes from both source roots in `target/classes/`

## 3. Implement Bootstrap class

- [x] 3.1 Create `Bootstrap.java` in `src/main/java-bootstrap/io/github/mavenmcp/` with `main(String[] args)` method
- [x] 3.2 Implement JVM version check using `Runtime.version().feature() >= 21`
- [x] 3.3 On version mismatch: print three-line error to stderr (`maven-mcp requires Java 21+`, `Detected: Java <version>`, `Install Java 21+: https://adoptium.net/`) and `System.exit(1)`
- [x] 3.4 On version OK: load `MavenMcpServer` via `Class.forName("io.github.mavenmcp.MavenMcpServer")`, get `main` method via reflection, invoke with `args`
- [x] 3.5 Wrap reflection delegation in try-catch: on failure print `Failed to start maven-mcp: <exception message>` to stderr and `System.exit(1)`

## 4. Update MANIFEST entry point

- [x] 4.1 Change `maven-shade-plugin` `mainClass` from `io.github.mavenmcp.MavenMcpServer` to `io.github.mavenmcp.Bootstrap`
- [x] 4.2 Verify `mvn clean package` produces a fat JAR with correct `Main-Class` in `MANIFEST.MF`

## 5. Tests

- [x] 5.1 Add unit test for `Bootstrap` verifying that the class file has Java 11 bytecode version (major version 55) by reading `Bootstrap.class` from classpath
- [x] 5.2 Add unit test verifying version check logic: mock or call the check method with a version below 21 and assert stderr output and exit behavior
- [x] 5.3 Verify all existing tests still pass (`mvn clean test`)
