package io.github.mavenmcp.tool;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.maven.MavenExecutionResult;
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
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(0, "[INFO] BUILD SUCCESS", "", 500));
        SyncToolSpecification spec = CleanTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String text = result.content().getFirst().toString();
        assertThat(text).contains("SUCCESS");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldReturnFailureWithOutputOnError() {
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(1, "[ERROR] Clean failed", "", 200));
        SyncToolSpecification spec = CleanTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String text = result.content().getFirst().toString();
        assertThat(text).contains("FAILURE");
        assertThat(text).contains("Clean failed");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldReturnIsErrorOnExecutionException() {
        var runner = new TestRunners.ThrowingRunner();
        SyncToolSpecification spec = CleanTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        assertThat(result.isError()).isTrue();
    }

    @Test
    void shouldPassArgsToRunner() {
        var runner = new TestRunners.CapturingRunner();
        SyncToolSpecification spec = CleanTool.create(config, runner, objectMapper);

        spec.call().apply(null, Map.of("args", List.of("-X", "-Pfoo")));

        assertThat(runner.capturedArgs).containsExactly("-X", "-Pfoo");
    }

}
