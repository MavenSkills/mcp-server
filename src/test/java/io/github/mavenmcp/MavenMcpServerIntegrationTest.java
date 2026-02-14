package io.github.mavenmcp;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MavenMcpServerIntegrationTest {

    @Test
    void shouldRespondToInitializeRequest() throws Exception {
        var clientToServer = new PipedOutputStream();
        var serverIn = new PipedInputStream(clientToServer);
        var serverOut = new ByteArrayOutputStream();

        var transport = new StdioServerTransportProvider(
                new JacksonMcpJsonMapper(new ObjectMapper()), serverIn, serverOut);

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("maven-mcp", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(Boolean.TRUE)
                        .logging()
                        .build())
                .build();

        try {
            String initRequest = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}\n";
            clientToServer.write(initRequest.getBytes(StandardCharsets.UTF_8));
            clientToServer.flush();

            // Wait for async processing
            Thread.sleep(2000);

            String response = serverOut.toString(StandardCharsets.UTF_8).trim();
            assertThat(response).isNotEmpty();

            JsonNode json = new ObjectMapper().readTree(response);
            assertThat(json.get("id").asInt()).isEqualTo(1);
            assertThat(json.path("result").path("serverInfo").path("name").asText())
                    .isEqualTo("maven-mcp");
            assertThat(json.path("result").path("protocolVersion").asText())
                    .isEqualTo("2024-11-05");
            assertThat(json.path("result").path("capabilities").path("tools").has("listChanged"))
                    .isTrue();
        } finally {
            server.close();
            clientToServer.close();
        }
    }
}
