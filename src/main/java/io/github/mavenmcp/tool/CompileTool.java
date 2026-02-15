package io.github.mavenmcp.tool;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.maven.MavenExecutionException;
import io.github.mavenmcp.maven.MavenExecutionResult;
import io.github.mavenmcp.maven.MavenRunner;
import io.github.mavenmcp.model.BuildResult;
import io.github.mavenmcp.parser.CompilationOutputParser;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP tool: maven_compile — compiles the Maven project and returns structured errors/warnings.
 */
public final class CompileTool {

    private static final Logger log = LoggerFactory.getLogger(CompileTool.class);

    private static final String TOOL_NAME = "maven_compile";
    private static final String DESCRIPTION =
            "Compile a Maven project. Returns structured compilation errors with file, line, column, and message.";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "args": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "Additional Maven CLI arguments (e.g. [\\"-DskipFrontend\\", \\"-Pdev\\"])"
                }
              }
            }
            """;

    private CompileTool() {
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
                        log.info("maven_compile called with args: {}", args);

                        MavenExecutionResult execResult = runner.execute(
                                "compile", args,
                                config.mavenExecutable(), config.projectDir());

                        // Parse compilation output
                        var parseResult = CompilationOutputParser.parse(
                                execResult.stdout(), config.projectDir());

                        String status = execResult.isSuccess() ? BuildResult.SUCCESS : BuildResult.FAILURE;
                        // Tail of raw output only on failure
                        String output = execResult.isSuccess() ? null
                                : ToolUtils.tailLines(execResult.stdout(), ToolUtils.DEFAULT_OUTPUT_TAIL_LINES);

                        var buildResult = new BuildResult(
                                status, execResult.duration(),
                                parseResult.errors(), parseResult.warnings(),
                                null, null, null, output, null);

                        String json = objectMapper.writeValueAsString(buildResult);
                        return new CallToolResult(List.of(new TextContent(json)), false);

                    } catch (MavenExecutionException e) {
                        log.error("maven_compile failed: {}", e.getMessage());
                        return new CallToolResult(
                                List.of(new TextContent("Error: " + e.getMessage())), true);
                    } catch (Exception e) {
                        log.error("Unexpected error in maven_compile", e);
                        return new CallToolResult(
                                List.of(new TextContent("Internal error: " + e.getMessage())), true);
                    }
                }
        );
    }
}
