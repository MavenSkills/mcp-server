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
import io.github.mavenmcp.maven.MavenRunner;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
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
        // Create a temp project dir with pom.xml for config validation
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        config = new ServerConfig(tempDir, Path.of("/usr/bin/mvn"));
        reportsDir = tempDir.resolve("target/surefire-reports");
    }

    @Test
    void shouldReturnTestResultsFromSurefireXml() throws IOException {
        Files.createDirectories(reportsDir);
        copyFixture("TEST-com.example.PassingTest.xml");

        var runner = new StubRunner(new MavenExecutionResult(0, "[INFO] BUILD SUCCESS", "", 5000));
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

        var runner = new StubRunner(new MavenExecutionResult(1, "[ERROR] Tests failed", "", 8000));
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("FAILURE");
        assertThat(json).contains("shouldReturnUser");
        assertThat(json).contains("\"testsFailed\":2");
        assertThat(json).contains("\"output\""); // raw output on failure
    }

    @Test
    void shouldFallbackToCompilationErrorsWhenNoXml() {
        // No surefire-reports directory → compilation failure fallback
        String stdout = "[ERROR] /tmp/src/main/java/Foo.java:[10,5] cannot find symbol\n[ERROR] BUILD FAILURE";
        var runner = new StubRunner(new MavenExecutionResult(1, stdout, "", 3000));
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("FAILURE");
        assertThat(json).contains("cannot find symbol");
        assertThat(json).doesNotContain("testsRun"); // no test summary
    }

    @Test
    void shouldPassTestFilterAsArg() {
        var runner = new CapturingRunner();
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        spec.call().apply(null, Map.of("testFilter", "MyTest#shouldWork"));

        assertThat(runner.capturedArgs).contains("-Dtest=MyTest#shouldWork");
        assertThat(runner.capturedArgs).contains("-DfailIfNoTests=false");
    }

    @Test
    void shouldPassExtraArgs() {
        var runner = new CapturingRunner();
        SyncToolSpecification spec = TestTool.create(config, runner, objectMapper);

        spec.call().apply(null, Map.of("args", List.of("-X")));

        assertThat(runner.capturedArgs).contains("-X");
    }

    private void copyFixture(String filename) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("surefire-reports/" + filename)) {
            if (is == null) throw new RuntimeException("Fixture not found: " + filename);
            Files.copy(is, reportsDir.resolve(filename));
        }
    }

    // --- Test helpers ---

    static class StubRunner extends MavenRunner {
        private final MavenExecutionResult result;
        StubRunner(MavenExecutionResult result) { this.result = result; }
        @Override
        public MavenExecutionResult execute(String goal, List<String> extraArgs, Path exe, Path dir) {
            return result;
        }
    }

    static class CapturingRunner extends MavenRunner {
        List<String> capturedArgs;
        @Override
        public MavenExecutionResult execute(String goal, List<String> extraArgs, Path exe, Path dir) {
            capturedArgs = extraArgs;
            return new MavenExecutionResult(0, "", "", 100);
        }
    }
}
