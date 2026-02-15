package io.github.mavenmcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mavenmcp.config.ServerConfig;
import io.github.mavenmcp.maven.MavenDetector;
import io.github.mavenmcp.maven.MavenNotFoundException;
import io.github.mavenmcp.maven.MavenRunner;
import io.github.mavenmcp.tool.CleanTool;
import io.github.mavenmcp.tool.CompileTool;
import io.github.mavenmcp.tool.TestTool;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Maven MCP Server entry point.
 * Exposes Maven CLI operations as MCP tools over stdio transport.
 */
@Command(
        name = "maven-mcp-server",
        description = "MCP server wrapping Maven CLI operations for AI agents",
        mixinStandardHelpOptions = true,
        version = "1.0.0"
)
public class MavenMcpServer implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(MavenMcpServer.class);
    private static final String SERVER_NAME = "maven-mcp";
    private static final String SERVER_VERSION = loadVersion();

    @Option(names = "--project", required = true,
            description = "Path to the Maven project directory")
    private Path projectDir;

    // Available to future tool handlers
    private ServerConfig config;
    private MavenRunner mavenRunner;

    @Override
    public Integer call() {
        // --- Startup validation ---
        try {
            config = validateAndCreateConfig();
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        mavenRunner = new MavenRunner();

        log.info("Maven MCP Server v{}", SERVER_VERSION);
        log.info("Project directory: {}", config.projectDir());
        log.info("Maven executable: {}", config.mavenExecutable());

        // --- MCP server bootstrap ---
        ObjectMapper objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        StdioServerTransportProvider transport =
                new StdioServerTransportProvider(new JacksonMcpJsonMapper(new ObjectMapper()));

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder()
                        .tools(Boolean.TRUE)
                        .logging()
                        .build())
                .tools(
                        CompileTool.create(config, mavenRunner, objectMapper),
                        CleanTool.create(config, mavenRunner, objectMapper),
                        TestTool.create(config, mavenRunner, objectMapper)
                )
                .build();

        log.info("MCP server started, listening on stdio");

        // Server blocks on stdio until client disconnects.
        // StdioServerTransportProvider handles the lifecycle.
        return 0;
    }

    private ServerConfig validateAndCreateConfig() {
        // 1. Project directory exists
        if (!Files.isDirectory(projectDir)) {
            throw new IllegalStateException(
                    "Project directory does not exist: " + projectDir);
        }

        // 2. pom.xml present
        Path pomXml = projectDir.resolve("pom.xml");
        if (!Files.isRegularFile(pomXml)) {
            throw new IllegalStateException(
                    "No pom.xml found in project directory: " + projectDir);
        }

        // 3. Maven executable available
        Path mavenExecutable;
        try {
            mavenExecutable = MavenDetector.detect(projectDir);
        } catch (MavenNotFoundException e) {
            throw new IllegalStateException(e.getMessage());
        }

        return new ServerConfig(projectDir.toAbsolutePath(), mavenExecutable);
    }

    public ServerConfig getConfig() {
        return config;
    }

    public MavenRunner getMavenRunner() {
        return mavenRunner;
    }


    private static String loadVersion() {
        try (var in = MavenMcpServer.class.getResourceAsStream("/maven-mcp.properties")) {
            if (in != null) {
                var props = new java.util.Properties();
                props.load(in);
                return props.getProperty("version", "unknown");
            }
        } catch (Exception e) {
            // fall through
        }
        return "unknown";
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MavenMcpServer()).execute(args);
        // Only exit on error. On success (exitCode == 0), let the MCP transport's
        // non-daemon threads keep the JVM alive until the client disconnects (stdin EOF).
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
