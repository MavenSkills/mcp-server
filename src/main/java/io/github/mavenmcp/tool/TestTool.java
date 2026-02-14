package io.github.mavenmcp.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.maven.MavenExecutionException;
import io.github.mavenmcp.maven.MavenExecutionResult;
import io.github.mavenmcp.maven.MavenRunner;
import io.github.mavenmcp.model.BuildResult;
import io.github.mavenmcp.parser.CompilationOutputParser;
import io.github.mavenmcp.parser.SurefireReportParser;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP tool: maven_test — runs Maven tests and returns structured results
 * parsed from Surefire XML reports.
 */
public final class TestTool {

    private static final Logger log = LoggerFactory.getLogger(TestTool.class);

    private static final String TOOL_NAME = "maven_test";
    private static final String DESCRIPTION =
            "Run Maven tests. Returns structured test results with pass/fail details, failure messages, and stack traces.";
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "testFilter": {
                  "type": "string",
                  "description": "Test filter: class name (MyTest), method (MyTest#method), or multiple (MyTest,OtherTest)"
                },
                "args": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "Additional Maven CLI arguments"
                },
                "stackTraceLines": {
                  "type": "integer",
                  "description": "Max stack trace lines per failure (default: 50)"
                }
              }
            }
            """;

    private TestTool() {
    }

    public static SyncToolSpecification create(ServerConfig config, MavenRunner runner,
                                               ObjectMapper objectMapper) {
        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        Tool tool = Tool.builder()
                .name(TOOL_NAME)
                .description(DESCRIPTION)
                .inputSchema(jsonMapper, INPUT_SCHEMA)
                .build();
        return new SyncToolSpecification(
                tool,
                (exchange, params) -> {
                    try {
                        List<String> args = buildArgs(params);
                        int stackTraceLines = extractStackTraceLines(params);
                        log.info("maven_test called with args: {}, stackTraceLines: {}", args, stackTraceLines);

                        MavenExecutionResult execResult = runner.execute(
                                "test", args,
                                config.mavenExecutable(), config.projectDir());

                        String status = execResult.isSuccess() ? BuildResult.SUCCESS : BuildResult.FAILURE;
                        String output = execResult.isSuccess() ? null : execResult.stdout();

                        // Try Surefire XML reports first
                        var surefireResult = SurefireReportParser.parse(
                                config.projectDir(), stackTraceLines);

                        BuildResult buildResult;
                        if (surefireResult != null) {
                            // Test results available from XML
                            buildResult = new BuildResult(
                                    status, execResult.duration(),
                                    null, null,
                                    surefireResult.summary(),
                                    surefireResult.failures().isEmpty() ? List.of() : surefireResult.failures(),
                                    null, output);
                        } else if (!execResult.isSuccess()) {
                            // No XML reports + failure = likely compilation error
                            var parseResult = CompilationOutputParser.parse(
                                    execResult.stdout(), config.projectDir());
                            buildResult = new BuildResult(
                                    status, execResult.duration(),
                                    parseResult.errors().isEmpty() ? List.of() : parseResult.errors(),
                                    parseResult.warnings().isEmpty() ? List.of() : parseResult.warnings(),
                                    null, null, null, output);
                        } else {
                            // Success but no XML (shouldn't happen normally)
                            buildResult = new BuildResult(
                                    status, execResult.duration(),
                                    null, null, null, null, null, null);
                        }

                        String json = objectMapper.writeValueAsString(buildResult);
                        return new CallToolResult(List.of(new TextContent(json)), false);

                    } catch (MavenExecutionException e) {
                        log.error("maven_test failed: {}", e.getMessage());
                        return new CallToolResult(
                                List.of(new TextContent("Error: " + e.getMessage())), true);
                    } catch (Exception e) {
                        log.error("Unexpected error in maven_test", e);
                        return new CallToolResult(
                                List.of(new TextContent("Internal error: " + e.getMessage())), true);
                    }
                }
        );
    }

    private static List<String> buildArgs(Map<String, Object> params) {
        List<String> args = new ArrayList<>(ToolUtils.extractArgs(params));

        Object testFilter = params.get("testFilter");
        if (testFilter instanceof String filter && !filter.isBlank()) {
            args.add("-Dtest=" + filter);
            args.add("-DfailIfNoTests=false");
        }

        return args;
    }

    private static int extractStackTraceLines(Map<String, Object> params) {
        Object value = params.get("stackTraceLines");
        if (value instanceof Number num) {
            return num.intValue();
        }
        return SurefireReportParser.DEFAULT_STACK_TRACE_LINES;
    }
}
