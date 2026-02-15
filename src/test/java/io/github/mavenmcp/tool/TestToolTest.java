package io.github.mavenmcp.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.maven.MavenExecutionResult;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TestToolTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private ServerConfig config;
    private Path reportsDir;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        config = new ServerConfig(tempDir, Path.of("/usr/bin/mvn"));
        reportsDir = tempDir.resolve("target/surefire-reports");
        // Create target/test-classes so the testOnly pre-flight guard passes (default is now true)
        Files.createDirectories(tempDir.resolve("target/test-classes"));
    }

    @Test
    void shouldReturnTestResultsFromSurefireXml() throws IOException {
        Files.createDirectories(reportsDir);

        var runner = new TestRunners.StubRunner(
                new MavenExecutionResult(0, "[INFO] BUILD SUCCESS", "", 5000),
                () -> copyFixtureUnchecked("TEST-com.example.PassingTest.xml"));
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("SUCCESS");
        assertThat(json).contains("\"testsRun\":3");
        assertThat(json).contains("\"testsFailed\":0");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldReturnFailuresFromSurefireXml() throws IOException {
        Files.createDirectories(reportsDir);

        var runner = new TestRunners.StubRunner(
                new MavenExecutionResult(1, "[ERROR] Tests failed", "", 8000),
                () -> copyFixtureUnchecked("TEST-com.example.FailingTest.xml"));
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("FAILURE");
        assertThat(json).contains("shouldReturnUser");
        assertThat(json).contains("\"testsFailed\":2");
        assertThat(json).doesNotContain("\"output\""); // null when surefire XML available
    }

    @Test
    void shouldFallbackToCompilationErrorsWhenNoXml() {
        // No surefire-reports directory → compilation failure fallback
        String stdout = "[ERROR] /tmp/src/main/java/Foo.java:[10,5] cannot find symbol\n[ERROR] BUILD FAILURE";
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(1, stdout, "", 3000));
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("FAILURE");
        assertThat(json).contains("cannot find symbol");
        assertThat(json).doesNotContain("testsRun"); // no test summary
    }

    @Test
    void shouldPassTestFilterAsArg() {
        var runner = new TestRunners.CapturingRunner();
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        spec.call().apply(null, Map.of("testFilter", "MyTest#shouldWork"));

        assertThat(runner.capturedArgs).contains("-Dtest=MyTest#shouldWork");
        assertThat(runner.capturedArgs).contains("-DfailIfNoTests=false");
    }

    @Test
    void shouldPassExtraArgs() {
        var runner = new TestRunners.CapturingRunner();
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        spec.call().apply(null, Map.of("args", List.of("-X")));

        assertThat(runner.capturedArgs).contains("-X");
    }

    @Nested
    class SmartStackTraces {

        @Test
        void shouldApplySmartStackTraceProcessing() throws IOException {
            Files.createDirectories(reportsDir);
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <testsuite name="com.example.ChainTest" time="1.0" tests="1" errors="0" skipped="0" failures="1">
                      <testcase name="shouldWork" classname="com.example.ChainTest" time="0.1">
                        <failure message="top" type="java.lang.RuntimeException">java.lang.RuntimeException: top
                    \tat org.springframework.web.client.RestTemplate.execute(RestTemplate.java:100)
                    \tat com.example.service.ApiClient.call(ApiClient.java:23)
                    Caused by: java.io.IOException: root cause
                    \tat com.example.service.ApiClient.openConnection(ApiClient.java:45)
                    \tat java.net.Socket.connect(Socket.java:591)</failure>
                      </testcase>
                    </testsuite>
                    """;

            var runner = new TestRunners.StubRunner(
                    new MavenExecutionResult(1, "[ERROR] Tests failed", "", 5000),
                    () -> {
                        try {
                            Files.writeString(reportsDir.resolve("TEST-com.example.ChainTest.xml"), xml);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null,
                    Map.of("appPackage", "com.example"));

            String json = result.content().getFirst().toString();
            // Both exception headers should be preserved
            assertThat(json).contains("RuntimeException: top");
            assertThat(json).contains("Caused by: java.io.IOException: root cause");
            // Application frames should be preserved
            assertThat(json).contains("com.example.service.ApiClient");
            // Framework frames should be collapsed
            assertThat(json).contains("framework frames omitted");
        }
    }

    @Nested
    class TestLogExtraction {

        @Test
        void shouldIncludeTestLogsInResponse() throws IOException {
            Files.createDirectories(reportsDir);

            var runner = new TestRunners.StubRunner(
                    new MavenExecutionResult(1, "[ERROR] Tests failed", "", 5000),
                    () -> copyFixtureUnchecked("TEST-com.example.FailingTestWithLogs.xml"));
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of());

            String json = result.content().getFirst().toString();
            assertThat(json).contains("testOutput");
            assertThat(json).contains("Initializing connection pool");
        }

        @Test
        void shouldExcludeTestLogsWhenDisabled() throws IOException {
            Files.createDirectories(reportsDir);

            var runner = new TestRunners.StubRunner(
                    new MavenExecutionResult(1, "[ERROR] Tests failed", "", 5000),
                    () -> copyFixtureUnchecked("TEST-com.example.FailingTestWithLogs.xml"));
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null,
                    Map.of("includeTestLogs", false));

            String json = result.content().getFirst().toString();
            assertThat(json).doesNotContain("testOutput");
        }
    }

    @Nested
    class OutputHandling {

        @Test
        void shouldOmitOutputWhenSurefireXmlAvailable() throws IOException {
            Files.createDirectories(reportsDir);

            String rawOutput = """
                    [INFO] Scanning for projects...
                    [ERROR] Tests run: 4, Failures: 2, Errors: 0, Skipped: 0
                    [ERROR] BUILD FAILURE""";
            var runner = new TestRunners.StubRunner(
                    new MavenExecutionResult(1, rawOutput, "", 5000),
                    () -> copyFixtureUnchecked("TEST-com.example.FailingTest.xml"));
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of());

            String json = result.content().getFirst().toString();
            // Structured data present, raw output omitted
            assertThat(json).contains("\"failures\"");
            assertThat(json).doesNotContain("\"output\"");
        }
    }

    @Nested
    class AppPackageDerivation {

        @Test
        void shouldDeriveAppPackageFromPomXml() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project>
                      <groupId>io.github.mavenmcp</groupId>
                      <artifactId>test-project</artifactId>
                      <version>1.0</version>
                    </project>
                    """);

            String derived = TestTool.extractAppPackage(Map.of(), tempDir);

            assertThat(derived).isEqualTo("io.github.mavenmcp");
        }

        @Test
        void shouldUseExplicitAppPackageOverDerived() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project>
                      <groupId>io.github.mavenmcp</groupId>
                      <artifactId>test-project</artifactId>
                      <version>1.0</version>
                    </project>
                    """);

            String explicit = TestTool.extractAppPackage(
                    Map.of("appPackage", "com.custom.pkg"), tempDir);

            assertThat(explicit).isEqualTo("com.custom.pkg");
        }

        @Test
        void shouldReturnNullWhenNoPomXml() {
            String derived = TestTool.extractAppPackage(Map.of(), tempDir.resolve("nonexistent"));

            assertThat(derived).isNull();
        }
    }

    @Nested
    class TestOnlyMode {

        @Test
        void shouldUseSurefireGoalByDefault() {
            var runner = new TestRunners.CapturingRunner();
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            spec.call().apply(null, Map.of());

            assertThat(runner.capturedGoal).isEqualTo("surefire:test");
        }

        @Test
        void shouldUseTestGoalWhenTestOnlyFalse() throws Exception {
            var runner = new TestRunners.CapturingRunner();
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of("testOnly", false));

            assertThat(runner.capturedGoal).isEqualTo("test");
            // note should be null when testOnly=false
            String json = result.content().getFirst().toString();
            assertThat(json).doesNotContain("\"note\"");
        }

        @Test
        void shouldReturnErrorWhenTestOnlyTrueAndNoTestClasses() throws IOException {
            // Remove target/test-classes created by setUp
            Files.delete(tempDir.resolve("target/test-classes"));
            var runner = new TestRunners.CapturingRunner();
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of("testOnly", true));

            assertThat(result.isError()).isTrue();
            assertThat(result.content().getFirst().toString())
                    .contains("Project not compiled. Run maven_compile first or set testOnly=false.");
            assertThat(runner.capturedGoal).isNull(); // Maven was never invoked
        }

        @Test
        void shouldCombineTestOnlyWithTestFilter() {
            var runner = new TestRunners.CapturingRunner();
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            spec.call().apply(null, Map.of("testOnly", true, "testFilter", "MyTest"));

            assertThat(runner.capturedGoal).isEqualTo("surefire:test");
            assertThat(runner.capturedArgs).contains("-Dtest=MyTest");
            assertThat(runner.capturedArgs).contains("-DfailIfNoTests=false");
        }

        @Test
        void shouldIncludeNoteWhenTestOnlyTrue() throws Exception {
            var runner = new TestRunners.CapturingRunner();
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of("testOnly", true));

            String json = result.content().getFirst().toString();
            assertThat(json).contains("Ran in testOnly mode (surefire:test)");
            assertThat(json).contains("re-run with testOnly=false for a full build");
        }

        @Test
        void shouldAutoRecompileWhenStaleClassesDetected() throws Exception {
            // Create class file first, then source file (source is newer = stale)
            Path classesDir = tempDir.resolve("target/classes");
            Files.createDirectories(classesDir);
            Files.writeString(classesDir.resolve("Foo.class"), "bytecode");

            Thread.sleep(50); // ensure timestamp difference

            Path srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("Foo.java"), "source");

            var runner = new TestRunners.CapturingRunner();
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of("testOnly", true));

            // Should have two invocations: recompile + surefire:test
            assertThat(runner.allGoals).containsExactly(
                    "compiler:compile compiler:testCompile", "surefire:test");
            String json = result.content().getFirst().toString();
            assertThat(json).contains("auto-recompiled via compiler:compile compiler:testCompile");
        }

        @Test
        void shouldReturnCompilationErrorWhenAutoRecompileFails() throws Exception {
            // Create stale classes
            Path classesDir = tempDir.resolve("target/classes");
            Files.createDirectories(classesDir);
            Files.writeString(classesDir.resolve("Foo.class"), "bytecode");

            Thread.sleep(50);

            Path srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("Foo.java"), "source");

            var runner = new TestRunners.CapturingRunner();
            runner.failOnGoal("compiler:compile compiler:testCompile");
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of("testOnly", true));

            // Only recompile invocation, no surefire:test
            assertThat(runner.allGoals).containsExactly("compiler:compile compiler:testCompile");
            String json = result.content().getFirst().toString();
            assertThat(json).contains("FAILURE");
        }
    }

    @Nested
    class StaleClassesDetection {

        @Test
        void shouldDetectStaleWhenSourceNewerThanClass() throws Exception {
            Path classesDir = tempDir.resolve("target/classes");
            Files.createDirectories(classesDir);
            Files.writeString(classesDir.resolve("Foo.class"), "bytecode");

            Thread.sleep(50);

            Path srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("Foo.java"), "source");

            assertThat(TestTool.checkStaleClasses(tempDir)).isTrue();
        }

        @Test
        void shouldNotDetectStaleWhenClassNewerThanSource() throws Exception {
            Path srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("Foo.java"), "source");

            Thread.sleep(50);

            Path classesDir = tempDir.resolve("target/classes");
            Files.createDirectories(classesDir);
            Files.writeString(classesDir.resolve("Foo.class"), "bytecode");

            assertThat(TestTool.checkStaleClasses(tempDir)).isFalse();
        }

        @Test
        void shouldNotDetectStaleWhenSrcMissing() throws IOException {
            Files.createDirectories(tempDir.resolve("target/classes"));
            assertThat(TestTool.checkStaleClasses(tempDir)).isFalse();
        }

        @Test
        void shouldNotDetectStaleWhenClassesMissing() throws Exception {
            Path srcDir = tempDir.resolve("src/main/java");
            Files.createDirectories(srcDir);
            Files.writeString(srcDir.resolve("Foo.java"), "source");

            assertThat(TestTool.checkStaleClasses(tempDir)).isFalse();
        }
    }

    @Nested
    class SurefireReportCleanup {

        @Test
        void shouldDeleteStaleXmlBeforeTestExecution() throws IOException {
            Files.createDirectories(reportsDir);
            // Place a stale XML file
            Files.writeString(reportsDir.resolve("TEST-com.example.StaleTest.xml"),
                    "<testsuite tests=\"100\" failures=\"0\" errors=\"0\"/>");
            // Also place the fixture that the runner "produces"
            copyFixture("TEST-com.example.PassingTest.xml");

            var runner = new TestRunners.StubRunner(
                    new MavenExecutionResult(0, "[INFO] BUILD SUCCESS", "", 5000));
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of());

            // The stale XML should have been deleted before execution;
            // SurefireReportParser won't find it because cleanSurefireReports runs before execute.
            // Since StubRunner doesn't actually create XML files, the reports dir should be empty.
            assertThat(reportsDir.resolve("TEST-com.example.StaleTest.xml")).doesNotExist();
            assertThat(reportsDir.resolve("TEST-com.example.PassingTest.xml")).doesNotExist();
        }

        @Test
        void shouldBeNoOpWhenReportsDirDoesNotExist() {
            // No target/surefire-reports directory exists
            TestTool.cleanSurefireReports(tempDir);
            // Should not throw — just a no-op
            assertThat(tempDir.resolve("target/surefire-reports")).doesNotExist();
        }

        @Test
        void shouldNotDeleteNonTestXmlFiles() throws IOException {
            Files.createDirectories(reportsDir);
            Path otherFile = reportsDir.resolve("failsafe-summary.xml");
            Files.writeString(otherFile, "<summary/>");

            TestTool.cleanSurefireReports(tempDir);

            assertThat(otherFile).exists();
        }
    }

    private void copyFixture(String filename) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("surefire-reports/" + filename)) {
            if (is == null) throw new RuntimeException("Fixture not found: " + filename);
            Files.copy(is, reportsDir.resolve(filename));
        }
    }

    private void copyFixtureUnchecked(String filename) {
        try {
            copyFixture(filename);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
