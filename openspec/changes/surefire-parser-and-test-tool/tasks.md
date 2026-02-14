## 1. Test Models

- [x] 1.1 Create `TestSummary.java` record in `model` package: fields `testsRun` (int), `testsFailed` (int), `testsSkipped` (int), `testsErrored` (int).
- [x] 1.2 Create `TestFailure.java` record in `model` package: fields `testClass` (String), `testMethod` (String), `message` (String), `stackTrace` (String, nullable). Annotate with `@JsonInclude(NON_NULL)`.
- [x] 1.3 Update `BuildResult.java`: change `summary` field type from `Object` to `TestSummary`, change `failures` field type from `List<?>` to `List<TestFailure>`. Verify existing tests still pass.

## 2. Surefire Report Parser

- [x] 2.1 Create sample Surefire XML fixture files in `src/test/resources/surefire-reports/`: `TEST-com.example.PassingTest.xml` (all pass), `TEST-com.example.FailingTest.xml` (with failures), `TEST-com.example.ErrorTest.xml` (with errors), `TEST-com.example.SkippedTest.xml` (with skipped tests).
- [x] 2.2 Create `SurefireReportParser.java` in `parser` package with method `parse(Path projectDir, int stackTraceLines) → SurefireResult` (record with TestSummary + List<TestFailure>). Return null if no reports directory or no XML files found.
- [x] 2.3 Implement XML parsing: use JAXP DocumentBuilder to parse each `TEST-*.xml` file, extract testsuite attributes (tests, failures, errors, skipped), extract testcase failures/errors with message and stack trace.
- [x] 2.4 Implement stack trace truncation: limit each stack trace to `stackTraceLines` lines. Default 50.
- [x] 2.5 Implement aggregation: sum test counts across all XML files, collect all failures into a single list.
- [x] 2.6 Write unit test `SurefireReportParserTest`: test with all-pass XML, failure XML, error XML, skipped XML, missing directory (returns null), empty directory (returns null), stack trace truncation.

## 3. Test Tool

- [x] 3.1 Create `TestTool.java` in `tool` package with static method `create(ServerConfig, MavenRunner, ObjectMapper) → SyncToolSpecification`. JSON schema with optional `testFilter` (string), `stackTraceLines` (integer), and `args` (array) parameters.
- [x] 3.2 Implement handler: extract params, build Maven args (add `-Dtest=<filter>` if testFilter provided, add `-DfailIfNoTests=false`), execute Maven, then branch: if Surefire XML exists → parse test results into summary/failures; if not → parse compilation errors from stdout.
- [x] 3.3 Include raw output only on FAILURE.
- [x] 3.4 Write unit test `TestToolTest` with stub MavenRunner: test success (all pass), test failure (with surefire XML), test compilation failure (no XML → fallback), test filter passthrough, test args passthrough.

## 4. Server Integration

- [x] 4.1 Modify `MavenMcpServer.java`: register `maven_test` tool alongside compile and clean.
- [x] 4.2 Verify: `mvn compile` — clean compilation.
- [x] 4.3 Verify: `mvn test` — all tests pass (existing + new).
- [x] 4.4 Verify: `mvn package` — fat JAR builds.
