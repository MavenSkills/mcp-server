package io.github.mavenmcp.tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.formatter.MarkdownFormatter;
import io.github.mavenmcp.maven.MavenExecutionException;
import io.github.mavenmcp.maven.MavenExecutionResult;
import io.github.mavenmcp.maven.MavenRunner;
import io.github.mavenmcp.model.BuildResult;
import io.github.mavenmcp.model.TestFailure;
import io.github.mavenmcp.parser.CompilationOutputParser;
import io.github.mavenmcp.parser.StackTraceProcessor;
import io.github.mavenmcp.parser.SurefireReportParser;
import io.github.mavenmcp.parser.TestFailureDeduplicator;
import io.github.mavenmcp.parser.XmlUtils;
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
                },
                "testOnly": {
                  "type": "boolean",
                  "description": "Default: true (skips lifecycle, runs surefire:test directly with auto-recompile if sources changed). Set to false when changes go beyond Java source code — e.g., build config (pom.xml), generated source templates, new dependencies, or resource files that affect compilation."
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
                        boolean testOnly = ToolUtils.extractBoolean(params, "testOnly", true);

                        String goal = testOnly ? "surefire:test" : "test";
                        String note = null;

                        // Pre-flight guard: surefire:test requires compiled classes
                        if (testOnly && !Files.isDirectory(config.projectDir().resolve("target/test-classes"))) {
                            return new CallToolResult(
                                    List.of(new TextContent("Project not compiled. Run maven_compile first or set testOnly=false.")),
                                    true);
                        }

                        // Stale-classes detection and auto-recompile (testOnly mode only)
                        if (testOnly) {
                            if (checkStaleClasses(config.projectDir())) {
                                log.info("Stale classes detected, auto-recompiling via compiler:compile compiler:testCompile");
                                MavenExecutionResult recompileResult = runner.execute(
                                        "compiler:compile compiler:testCompile", List.of(),
                                        config.mavenExecutable(), config.projectDir());

                                if (!recompileResult.isSuccess()) {
                                    var parseResult = CompilationOutputParser.parse(
                                            recompileResult.stdout(), config.projectDir());
                                    String output = ToolUtils.tailLines(recompileResult.stdout(),
                                            ToolUtils.DEFAULT_OUTPUT_TAIL_LINES);
                                    var buildResult = new BuildResult(
                                            BuildResult.FAILURE, recompileResult.duration(),
                                            parseResult.errors(), parseResult.warnings(),
                                            null, null, null, output, null);
                                    String markdown = MarkdownFormatter.format(buildResult, "Test");
                                    return new CallToolResult(List.of(new TextContent(markdown)), false);
                                }

                                note = "Ran in testOnly mode. Stale sources detected — auto-recompiled via "
                                        + "compiler:compile compiler:testCompile (generate-sources was skipped). "
                                        + "If tests still fail unexpectedly, re-run with testOnly=false for a full build.";
                            } else {
                                note = "Ran in testOnly mode (surefire:test). Lifecycle phases (generate-sources, compile) "
                                        + "were skipped. If tests fail unexpectedly, re-run with testOnly=false for a full build.";
                            }
                        }

                        log.info("maven_test called with goal: {}, args: {}, stackTraceLines: {}, appPackage: {}",
                                goal, args, stackTraceLines, appPackage);

                        cleanSurefireReports(config.projectDir());

                        MavenExecutionResult execResult = runner.execute(
                                goal, args,
                                config.mavenExecutable(), config.projectDir());

                        String status = execResult.isSuccess() ? BuildResult.SUCCESS : BuildResult.FAILURE;

                        // Try Surefire XML reports first
                        var surefireResult = SurefireReportParser.parse(
                                config.projectDir(), includeTestLogs, testOutputLimit);

                        BuildResult buildResult;
                        if (surefireResult.isPresent()) {
                            // Structured data available — no raw output needed
                            var sr = surefireResult.get();
                            var processedFailures = processStackTraces(
                                    sr.failures(), appPackage, stackTraceLines);
                            var deduplicatedFailures = TestFailureDeduplicator.deduplicate(processedFailures);
                            buildResult = new BuildResult(
                                    status, execResult.duration(),
                                    null, null,
                                    sr.summary(), deduplicatedFailures,
                                    null, null, note);
                        } else if (!execResult.isSuccess()) {
                            // No XML reports + failure = likely compilation error; tail raw output
                            String output = ToolUtils.tailLines(execResult.stdout(),
                                    ToolUtils.DEFAULT_OUTPUT_TAIL_LINES);
                            var parseResult = CompilationOutputParser.parse(
                                    execResult.stdout(), config.projectDir());
                            buildResult = new BuildResult(
                                    status, execResult.duration(),
                                    parseResult.errors(), parseResult.warnings(),
                                    null, null, null, output, note);
                        } else {
                            // Success but no XML (shouldn't happen normally)
                            buildResult = new BuildResult(
                                    status, execResult.duration(),
                                    null, null, null, null, null, null, note);
                        }

                        String markdown = MarkdownFormatter.format(buildResult, "Test");
                        return new CallToolResult(List.of(new TextContent(markdown)), false);

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

    /**
     * Check if compiled classes are stale by comparing newest .java timestamp
     * under src/ against newest .class timestamp under target/classes/.
     */
    static boolean checkStaleClasses(Path projectDir) {
        try {
            Path srcDir = projectDir.resolve("src");
            Path classesDir = projectDir.resolve("target/classes");

            if (!Files.isDirectory(srcDir) || !Files.isDirectory(classesDir)) {
                return false;
            }

            Optional<FileTime> newestSource = newestFileTime(srcDir, ".java");
            Optional<FileTime> newestClass = newestFileTime(classesDir, ".class");

            return newestSource.isPresent() && newestClass.isPresent()
                    && newestSource.get().compareTo(newestClass.get()) > 0;

        } catch (IOException e) {
            log.debug("Failed to check stale classes: {}", e.getMessage());
            return false;
        }
    }

    private static Optional<FileTime> newestFileTime(Path dir, String extension) throws IOException {
        try (var stream = Files.walk(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(extension))
                    .map(p -> {
                        try { return Files.getLastModifiedTime(p); }
                        catch (IOException e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());
        }
    }

    /**
     * Delete all TEST-*.xml files from target/surefire-reports/ to prevent stale results.
     * Best-effort: errors are silently ignored.
     */
    static void cleanSurefireReports(Path projectDir) {
        Path reportsDir = projectDir.resolve("target/surefire-reports");
        if (!Files.isDirectory(reportsDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(reportsDir, "TEST-*.xml")) {
            for (Path file : stream) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            log.debug("Failed to clean surefire reports: {}", e.getMessage());
        }
    }
}
