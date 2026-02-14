package io.github.mavenmcp.tool;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.maven.MavenExecutionException;
import io.github.mavenmcp.maven.MavenExecutionResult;
import io.github.mavenmcp.maven.MavenRunner;
import io.github.mavenmcp.model.BuildResult;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP tool: maven_clean — cleans the Maven project build directory.
 */
public final class CleanTool {

    private static final Logger log = LoggerFactory.getLogger(CleanTool.class);

    private static final String TOOL_NAME = "maven_clean";
    private static final String DESCRIPTION = "Clean the Maven project build directory (target/).";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "args": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "Additional Maven CLI arguments"
                }
              }
            }
            """;

    private CleanTool() {
    }

    public static SyncToolSpecification create(ServerConfig config, MavenRunner runner,
                                               ObjectMapper objectMapper) {
        var jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        Tool tool = Tool.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTION)
                .inputSchema(jsonMapper, INPUT_SCHEMA)
                .build();
        return new SyncToolSpecification(
                tool,
                (exchange, params) -> {
                    try {
                        List<String> args = ToolUtils.extractArgs(params);
                        log.info("maven_clean called with args: {}", args);

                        MavenExecutionResult execResult = runner.execute(
                                "clean", args,
                                config.mavenExecutable(), config.projectDir());

                        String status = execResult.isSuccess() ? BuildResult.SUCCESS : BuildResult.FAILURE;
                        String output = execResult.isSuccess() ? null : execResult.stdout();

                        var buildResult = new BuildResult(
                                status, execResult.duration(),
                                null, null, null, null, null, output);

                        String json = objectMapper.writeValueAsString(buildResult);
                        return new CallToolResult(List.of(new TextContent(json)), false);

                    } catch (MavenExecutionException e) {
                        log.error("maven_clean failed: {}", e.getMessage());
                        return new CallToolResult(
                                List.of(new TextContent("Error: " + e.getMessage())), true);
                    } catch (Exception e) {
                        log.error("Unexpected error in maven_clean", e);
                        return new CallToolResult(
                                List.of(new TextContent("Internal error: " + e.getMessage())), true);
                    }
                }
        );
    }
}
