package io.github.mavenmcp.model;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldOmitNullFieldsFromJson() throws Exception {
        var result = new BuildResult(
                BuildResult.SUCCESS, 1200,
                List.of(), List.of(),
                null, null, null, null
        );

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"status\":\"SUCCESS\"");
        assertThat(json).contains("\"duration\":1200");
        assertThat(json).doesNotContain("\"summary\"");
        assertThat(json).doesNotContain("\"failures\"");
        assertThat(json).doesNotContain("\"artifact\"");
        assertThat(json).doesNotContain("\"output\"");
    }

    @Test
    void shouldIncludeNonNullFields() throws Exception {
        var error = new CompilationError("src/main/java/Foo.java", 42, 15, "cannot find symbol", "ERROR");
        var result = new BuildResult(
                BuildResult.FAILURE, 3000,
                List.of(error), List.of(),
                null, null, null, "raw output here"
        );

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"status\":\"FAILURE\"");
        assertThat(json).contains("\"errors\"");
        assertThat(json).contains("\"output\":\"raw output here\"");
        assertThat(json).contains("\"file\":\"src/main/java/Foo.java\"");
        assertThat(json).contains("\"line\":42");
        assertThat(json).contains("\"column\":15");
    }

    @Test
    void shouldOmitNullColumnInCompilationError() throws Exception {
        var error = new CompilationError("Foo.java", 10, null, "some error", "ERROR");

        String json = mapper.writeValueAsString(error);

        assertThat(json).doesNotContain("\"column\"");
        assertThat(json).contains("\"line\":10");
    }
}
