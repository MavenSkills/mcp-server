package io.github.mavenmcp.tool;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.maven.MavenExecutionException;
import io.github.mavenmcp.maven.MavenExecutionResult;
import io.github.mavenmcp.maven.MavenRunner;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CleanToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final ServerConfig config = new ServerConfig(
            Path.of("/tmp/test-project"), Path.of("/usr/bin/mvn"));

    @Test
    void shouldReturnSuccessOnCleanBuild() {
        var runner = new StubMavenRunner(new MavenExecutionResult(0, "[INFO] BUILD SUCCESS", "", 500));
        SyncToolSpecification spec = CleanTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("SUCCESS");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldReturnFailureWithOutputOnError() {
        var runner = new StubMavenRunner(new MavenExecutionResult(1, "[ERROR] Clean failed", "", 200));
        SyncToolSpecification spec = CleanTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("FAILURE");
        assertThat(json).contains("Clean failed");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldReturnIsErrorOnExecutionException() {
        var runner = new ThrowingMavenRunner();
        SyncToolSpecification spec = CleanTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        assertThat(result.isError()).isTrue();
    }

    @Test
    void shouldPassArgsToRunner() {
        var runner = new CapturingMavenRunner();
        SyncToolSpecification spec = CleanTool.create(config, runner, objectMapper);

        spec.call().apply(null, Map.of("args", List.of("-X", "-Pfoo")));

        assertThat(runner.capturedArgs).containsExactly("-X", "-Pfoo");
    }

    // --- Test helpers ---

    static class StubMavenRunner extends MavenRunner {
        private final MavenExecutionResult result;
        StubMavenRunner(MavenExecutionResult result) { this.result = result; }
        @Override
        public MavenExecutionResult execute(String goal, List<String> extraArgs, Path exe, Path dir) {
            return result;
        }
    }

    static class ThrowingMavenRunner extends MavenRunner {
        @Override
        public MavenExecutionResult execute(String goal, List<String> extraArgs, Path exe, Path dir) {
            throw new MavenExecutionException("Simulated failure", new RuntimeException(), 0);
        }
    }

    static class CapturingMavenRunner extends MavenRunner {
        List<String> capturedArgs;
        @Override
        public MavenExecutionResult execute(String goal, List<String> extraArgs, Path exe, Path dir) {
            capturedArgs = extraArgs;
            return new MavenExecutionResult(0, "", "", 100);
        }
    }
}
