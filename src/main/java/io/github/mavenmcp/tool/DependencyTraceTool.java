package io.github.mavenmcp.tool;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.formatter.DependencyPathFormatter;
import io.github.mavenmcp.maven.MavenExecutionException;
import io.github.mavenmcp.maven.MavenExecutionResult;
import io.github.mavenmcp.maven.MavenRunner;
import io.github.mavenmcp.model.DependencyNode;
import io.github.mavenmcp.model.DependencyPathResult;
import io.github.mavenmcp.parser.DependencyTreeParser;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP tool: maven_dependency_path — traces where a dependency comes from in the Maven dependency tree.
 */
public final class DependencyTraceTool {

    private static final Logger log = LoggerFactory.getLogger(DependencyTraceTool.class);

    private static final String TOOL_NAME = "maven_dependency_path";
    private static final String DESCRIPTION =
            "Trace where a dependency comes from in the Maven dependency tree. "
            + "Returns the path from project root to matching artifacts and any version conflicts.";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "artifact": {
                  "type": "string",
                  "description": "Artifact to search for (substring match on groupId:artifactId, e.g. 'jackson-databind', 'slf4j', 'com.google')"
                },
                "args": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "Additional Maven CLI arguments (e.g. [\\"-pl\\", \\"submodule\\"])"
                }
              },
              "required": ["artifact"]
            }
            """;

    private static final int MAX_PATHS = 10;

    private DependencyTraceTool() {
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
                        String artifact = extractArtifact(params);
                        if (artifact == null || artifact.isBlank()) {
                            return new CallToolResult(
                                    List.of(new TextContent("Error: 'artifact' parameter is required")), true);
                        }

                        List<String> args = ToolUtils.extractArgs(params);
                        log.info("maven_dependency_path called with artifact: '{}', args: {}", artifact, args);

                        MavenExecutionResult execResult = runner.execute(
                                "dependency:tree", prepareArgs(args),
                                config.mavenExecutable(), config.projectDir());

                        if (!execResult.isSuccess()) {
                            String output = ToolUtils.tailLines(execResult.stdout(),
                                    ToolUtils.DEFAULT_OUTPUT_TAIL_LINES);
                            return new CallToolResult(
                                    List.of(new TextContent("Error: dependency:tree failed\n\n" + output)), true);
                        }

                        DependencyNode root = DependencyTreeParser.parse(execResult.stdout());
                        if (root == null) {
                            return new CallToolResult(
                                    List.of(new TextContent("Error: could not parse dependency tree output")), true);
                        }

                        List<List<DependencyNode>> allPaths = DependencyTreeParser.findPaths(root, artifact);
                        List<DependencyPathResult.ConflictEntry> conflicts =
                                DependencyTreeParser.findConflicts(root, artifact);

                        int totalMatches = allPaths.size();
                        List<List<DependencyNode>> cappedPaths = allPaths.size() > MAX_PATHS
                                ? allPaths.subList(0, MAX_PATHS) : allPaths;

                        var pathResult = new DependencyPathResult(artifact, cappedPaths, conflicts, totalMatches);
                        String markdown = DependencyPathFormatter.format(pathResult);

                        return new CallToolResult(List.of(new TextContent(markdown)), false);

                    } catch (MavenExecutionException e) {
                        log.error("maven_dependency_path failed: {}", e.getMessage());
                        return new CallToolResult(
                                List.of(new TextContent("Error: " + e.getMessage())), true);
                    } catch (Exception e) {
                        log.error("Unexpected error in maven_dependency_path", e);
                        return new CallToolResult(
                                List.of(new TextContent("Internal error: " + e.getMessage())), true);
                    }
                }
        );
    }

    private static List<String> prepareArgs(List<String> extraArgs) {
        // Always add -Dverbose for conflict information
        var args = new java.util.ArrayList<>(List.of("-Dverbose"));
        args.addAll(extraArgs);
        return args;
    }

    @SuppressWarnings("unchecked")
    private static String extractArtifact(java.util.Map<String, Object> params) {
        Object artifact = params.get("artifact");
        return artifact instanceof String s ? s : null;
    }
}
