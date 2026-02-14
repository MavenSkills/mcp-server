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
    }

    @Test
    void shouldReturnTestResultsFromSurefireXml() throws IOException {
        Files.createDirectories(reportsDir);
        copyFixture("TEST-com.example.PassingTest.xml");

        var runner = new TestRunners.StubRunner(new MavenExecutionResult(0, "[INFO] BUILD SUCCESS", "", 5000));
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
        copyFixture("TEST-com.example.FailingTest.xml");

        var runner = new TestRunners.StubRunner(new MavenExecutionResult(1, "[ERROR] Tests failed", "", 8000));
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("FAILURE");
        assertThat(json).contains("shouldReturnUser");
        assertThat(json).contains("\"testsFailed\":2");
        assertThat(json).contains("\"output\""); // filtered output on failure
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
            // Write a fixture with a Caused by chain
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
            Files.writeString(reportsDir.resolve("TEST-com.example.ChainTest.xml"), xml);

            var runner = new TestRunners.StubRunner(
                    new MavenExecutionResult(1, "[ERROR] Tests failed", "", 5000));
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
            copyFixture("TEST-com.example.FailingTestWithLogs.xml");

            var runner = new TestRunners.StubRunner(
                    new MavenExecutionResult(1, "[ERROR] Tests failed", "", 5000));
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of());

            String json = result.content().getFirst().toString();
            assertThat(json).contains("testOutput");
            assertThat(json).contains("Initializing connection pool");
        }

        @Test
        void shouldExcludeTestLogsWhenDisabled() throws IOException {
            Files.createDirectories(reportsDir);
            copyFixture("TEST-com.example.FailingTestWithLogs.xml");

            var runner = new TestRunners.StubRunner(
                    new MavenExecutionResult(1, "[ERROR] Tests failed", "", 5000));
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null,
                    Map.of("includeTestLogs", false));

            String json = result.content().getFirst().toString();
            assertThat(json).doesNotContain("testOutput");
        }
    }

    @Nested
    class MavenOutputFiltering {

        @Test
        void shouldFilterMavenOutputOnFailure() throws IOException {
            Files.createDirectories(reportsDir);
            copyFixture("TEST-com.example.FailingTest.xml");

            String rawOutput = """
                    [INFO] Scanning for projects...
                    [INFO] --- maven-surefire-plugin:3.2.5:test (default-test) @ my-project ---
                    Downloading: https://repo.maven.apache.org/maven2/org/example/lib.jar
                    [ERROR] Tests run: 4, Failures: 2, Errors: 0, Skipped: 0
                    [ERROR] BUILD FAILURE""";
            var runner = new TestRunners.StubRunner(
                    new MavenExecutionResult(1, rawOutput, "", 5000));
            SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

            CallToolResult result = spec.call().apply(null, Map.of());

            String json = result.content().getFirst().toString();
            // Actionable lines preserved
            assertThat(json).contains("Tests run: 4, Failures: 2");
            assertThat(json).contains("BUILD FAILURE");
            // Noise filtered out
            assertThat(json).doesNotContain("Scanning for projects");
            assertThat(json).doesNotContain("Downloading:");
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

    private void copyFixture(String filename) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("surefire-reports/" + filename)) {
            if (is == null) throw new RuntimeException("Fixture not found: " + filename);
            Files.copy(is, reportsDir.resolve(filename));
        }
    }

}
