package io.github.mavenmcp.tool;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.maven.MavenExecutionException;
import io.github.mavenmcp.maven.MavenExecutionResult;
import io.github.mavenmcp.maven.MavenRunner;
import io.github.mavenmcp.model.BuildResult;
import io.github.mavenmcp.model.TestFailure;
import io.github.mavenmcp.parser.CompilationOutputParser;
import io.github.mavenmcp.parser.MavenOutputFilter;
import io.github.mavenmcp.parser.XmlUtils;
import io.github.mavenmcp.parser.StackTraceProcessor;
import io.github.mavenmcp.parser.TestFailureDeduplicator;
import io.github.mavenmcp.parser.SurefireReportParser;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

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
                  "description": "Max stack trace lines per failure (default: 50). 0 disables line cap."
                },
                "appPackage": {
                  "type": "string",
                  "description": "Application package prefix for smart stack trace filtering (e.g. 'com.example.myapp'). Auto-derived from pom.xml groupId if not provided."
                },
                "includeTestLogs": {
                  "type": "boolean",
                  "description": "Include stdout/stderr from failing tests (default: true)"
                },
                "testOutputLimit": {
                  "type": "integer",
                  "description": "Per-test character limit for stdout/stderr output (default: 2000)"
                }
              }
            }
            """;

    private TestTool() {
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
                        List<String> args = buildArgs(params);
                        int stackTraceLines = extractStackTraceLines(params);
                        String appPackage = extractAppPackage(params, config.projectDir());
                        boolean includeTestLogs = ToolUtils.extractBoolean(params, "includeTestLogs", true);
                        int testOutputLimit = ToolUtils.extractInt(params, "testOutputLimit",
                                SurefireReportParser.DEFAULT_PER_TEST_OUTPUT_LIMIT);
                        log.info("maven_test called with args: {}, stackTraceLines: {}, appPackage: {}",
                                args, stackTraceLines, appPackage);

                        MavenExecutionResult execResult = runner.execute(
                                "test", args,
                                config.mavenExecutable(), config.projectDir());

                        String status = execResult.isSuccess() ? BuildResult.SUCCESS : BuildResult.FAILURE;
                        String output = execResult.isSuccess() ? null
                                : MavenOutputFilter.filter(execResult.stdout());

                        // Try Surefire XML reports first
                        var surefireResult = SurefireReportParser.parse(
                                config.projectDir(), includeTestLogs, testOutputLimit);

                        BuildResult buildResult;
                        if (surefireResult.isPresent()) {
                            // Test results available from XML
                            var sr = surefireResult.get();
                            // Apply smart stack trace processing
                            var processedFailures = processStackTraces(
                                    sr.failures(), appPackage, stackTraceLines);
                            var deduplicatedFailures = TestFailureDeduplicator.deduplicate(processedFailures);
                            buildResult = new BuildResult(
                                    status, execResult.duration(),
                                    null, null,
                                    sr.summary(), deduplicatedFailures,
                                    null, output);
                        } else if (!execResult.isSuccess()) {
                            // No XML reports + failure = likely compilation error
                            var parseResult = CompilationOutputParser.parse(
                                    execResult.stdout(), config.projectDir());
                            buildResult = new BuildResult(
                                    status, execResult.duration(),
                                    parseResult.errors(), parseResult.warnings(),
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

    /**
     * Apply smart stack trace processing to all failures.
     */
    private static List<TestFailure> processStackTraces(List<TestFailure> failures,
                                                         String appPackage, int stackTraceLines) {
        return failures.stream()
                .map(f -> f.withStackTrace(
                        StackTraceProcessor.process(f.stackTrace(), appPackage, stackTraceLines)))
                .toList();
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
        return ToolUtils.extractInt(params, "stackTraceLines",
                SurefireReportParser.DEFAULT_STACK_TRACE_LINES);
    }

    /**
     * Extract appPackage from params, or derive from pom.xml groupId.
     */
    static String extractAppPackage(Map<String, Object> params, Path projectDir) {
        Object value = params.get("appPackage");
        if (value instanceof String pkg && !pkg.isBlank()) {
            return pkg;
        }
        return deriveGroupId(projectDir);
    }

    /**
     * Read groupId from pom.xml for use as application package prefix.
     */
    static String deriveGroupId(Path projectDir) {
        try {
            File pomFile = projectDir.resolve("pom.xml").toFile();
            if (!pomFile.exists()) {
                return null;
            }
            Document doc = XmlUtils.newSecureDocumentBuilder().parse(pomFile);

            // Look for direct child <groupId> of <project>
            NodeList groupIds = doc.getDocumentElement().getElementsByTagName("groupId");
            if (groupIds.getLength() > 0) {
                String groupId = groupIds.item(0).getTextContent();
                if (groupId != null && !groupId.isBlank()) {
                    return groupId.strip();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to derive groupId from pom.xml: {}", e.getMessage());
        }
        return null;
    }
}
