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

class CompileToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final ServerConfig config = new ServerConfig(
            Path.of("/home/user/my-project"), Path.of("/usr/bin/mvn"));

    @Test
    void shouldReturnSuccessWithNoWarnings() {
        String stdout = "[INFO] Compiling 5 source files\n[INFO] BUILD SUCCESS";
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(0, stdout, "", 3000));
        SyncToolSpecification spec = CompileTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("SUCCESS");
        assertThat(json).doesNotContain("\"output\""); // no output on success
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldReturnFailureWithParsedErrors() {
        String stdout = "[ERROR] /home/user/my-project/src/main/java/Foo.java:[42,15] cannot find symbol\n[ERROR] BUILD FAILURE";
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(1, stdout, "", 4000));
        SyncToolSpecification spec = CompileTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("FAILURE");
        assertThat(json).contains("cannot find symbol");
        assertThat(json).contains("src/main/java/Foo.java"); // relative path
        assertThat(json).contains("\"output\""); // raw output on failure
    }

    @Test
    void shouldReturnSuccessWithWarnings() {
        String stdout = "[WARNING] /home/user/my-project/src/main/java/Old.java:[10,5] [deprecation] deprecated method\n[INFO] BUILD SUCCESS";
        var runner = new TestRunners.StubRunner(new MavenExecutionResult(0, stdout, "", 2000));
        SyncToolSpecification spec = CompileTool.create(config, runner, objectMapper);

        CallToolResult result = spec.call().apply(null, Map.of());

        String json = result.content().getFirst().toString();
        assertThat(json).contains("SUCCESS");
        assertThat(json).contains("WARNING");
        assertThat(json).contains("deprecation");
        assertThat(json).doesNotContain("\"output\""); // no output on success
    }

    @Test
    void shouldPassArgsToRunner() {
        var runner = new TestRunners.CapturingRunner();
        SyncToolSpecification spec = CompileTool.create(config, runner, objectMapper);

        spec.call().apply(null, Map.of("args", List.of("-DskipFrontend")));

        assertThat(runner.capturedArgs).containsExactly("-DskipFrontend");
    }

}
