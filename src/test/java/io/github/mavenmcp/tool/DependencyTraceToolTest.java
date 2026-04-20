package io.github.mavenmcp.tool;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

class DependencyTraceToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final ServerConfig config = new ServerConfig(
            Path.of("/home/user/my-project"), Path.of("/usr/bin/mvn"));

    @Test
    void shouldReturnPathForMatchingArtifact() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(0, stdout, "", 5000));
        SyncToolSpecification spec = DependencyTraceTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of("artifact", "failureaccess"));

        String text = result.content().getFirst().toString();
        assertThat(text).contains("Dependency Path: failureaccess");
        assertThat(text).contains("failureaccess:1.0.2");
        assertThat(text).contains("guava");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldReturnNoMatchMessage() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(0, stdout, "", 3000));
        SyncToolSpecification spec = DependencyTraceTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of("artifact", "nonexistent"));

        String text = result.content().getFirst().toString();
        assertThat(text).contains("No dependencies matching \"nonexistent\" found");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldShowConflicts() {
        String stdout = loadFixture("dependency-tree/conflicts-tree.txt");
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(0, stdout, "", 4000));
        SyncToolSpecification spec = DependencyTraceTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of("artifact", "jackson-databind"));

        String text = result.content().getFirst().toString();
        assertThat(text).contains("Dependency Path: jackson-databind");
        assertThat(text).contains("Conflicts");
        assertThat(text).contains("omitted for conflict");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldReturnErrorOnMavenFailure() {
        String stdout = "[ERROR] BUILD FAILURE\n[ERROR] Invalid POM";
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(1, stdout, "", 2000));
        SyncToolSpecification spec = DependencyTraceTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of("artifact", "anything"));

        assertThat(result.isError()).isTrue();
        String text = result.content().getFirst().toString();
        assertThat(text).contains("dependency:tree failed");
    }

    @Test
    void shouldReturnErrorWhenArtifactMissing() {
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(0, "", "", 100));
        SyncToolSpecification spec = DependencyTraceTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        assertThat(result.isError()).isTrue();
        String text = result.content().getFirst().toString();
        assertThat(text).contains("'artifact' parameter is required");
    }

    @Test
    void shouldPassVerboseFlagAndExtraArgs() {
        var runner = new TestRunners.CapturingRunner();
        SyncToolSpecification spec = DependencyTraceTool.create(config, runner, objectMapper);

        spec.call().apply(null, Map.of("artifact", "test", "args", List.of("-pl", "sub")));

        assertThat(runner.capturedGoal).isEqualTo("dependency:tree");
        assertThat(runner.capturedArgs).containsExactly("-Dverbose", "-pl", "sub");
    }

    @Test
    void shouldReturnErrorOnMavenException() {
        var runner = new TestRunners.ThrowingRunner();
        SyncToolSpecification spec = DependencyTraceTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of("artifact", "test"));

        assertThat(result.isError()).isTrue();
        String text = result.content().getFirst().toString();
        assertThat(text).contains("Error:");
    }

    private String loadFixture(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load fixture: " + path, e);
        }
    }
}
