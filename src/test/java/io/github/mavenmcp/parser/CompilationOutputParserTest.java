package io.github.mavenmcp.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import io.github.mavenmcp.model.CompilationError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompilationOutputParserTest {

    private static final Path PROJECT_DIR = Path.of("/home/user/my-project");

    @Test
    void shouldParseSingleError() {
        String stdout = loadFixture("compilation-output/single-error.txt");

        var result = CompilationOutputParser.parse(stdout, PROJECT_DIR);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.warnings()).isEmpty();

        CompilationError error = result.errors().getFirst();
        assertThat(error.file()).isEqualTo("src/main/java/com/example/MyService.java");
        assertThat(error.line()).isEqualTo(42);
        assertThat(error.column()).isEqualTo(15);
        assertThat(error.message()).isEqualTo("cannot find symbol");
        assertThat(error.severity()).isEqualTo("ERROR");
    }

    @Test
    void shouldParseMultipleErrors() {
        String stdout = loadFixture("compilation-output/multiple-errors.txt");

        var result = CompilationOutputParser.parse(stdout, PROJECT_DIR);

        assertThat(result.errors()).hasSize(3);
        assertThat(result.warnings()).isEmpty();

        assertThat(result.errors().get(0).line()).isEqualTo(42);
        assertThat(result.errors().get(1).line()).isEqualTo(55);
        assertThat(result.errors().get(2).file()).isEqualTo("src/main/java/com/example/OtherClass.java");
    }

    @Test
    void shouldParseWarningsOnly() {
        String stdout = loadFixture("compilation-output/warnings-only.txt");

        var result = CompilationOutputParser.parse(stdout, PROJECT_DIR);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).hasSize(2);

        assertThat(result.warnings().get(0).severity()).isEqualTo("WARNING");
        assertThat(result.warnings().get(0).message()).contains("deprecation");
        assertThat(result.warnings().get(1).message()).contains("unchecked");
    }

    @Test
    void shouldParseMixedErrorsAndWarnings() {
        String stdout = loadFixture("compilation-output/mixed-errors-warnings.txt");

        var result = CompilationOutputParser.parse(stdout, PROJECT_DIR);

        assertThat(result.errors()).hasSize(2);
        assertThat(result.warnings()).hasSize(2);

        // Verify ordering matches appearance in output
        assertThat(result.errors().get(0).line()).isEqualTo(42);
        assertThat(result.errors().get(1).line()).isEqualTo(55);
        assertThat(result.warnings().get(0).line()).isEqualTo(10);
        assertThat(result.warnings().get(1).line()).isEqualTo(25);
    }

    @Test
    void shouldReturnEmptyResultForCleanSuccess() {
        String stdout = loadFixture("compilation-output/clean-success.txt");

        var result = CompilationOutputParser.parse(stdout, PROJECT_DIR);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void shouldHandleErrorWithoutColumn() {
        String stdout = loadFixture("compilation-output/error-without-column.txt");

        var result = CompilationOutputParser.parse(stdout, PROJECT_DIR);

        assertThat(result.errors()).hasSize(1);
        CompilationError error = result.errors().getFirst();
        assertThat(error.line()).isEqualTo(42);
        assertThat(error.column()).isNull();
        assertThat(error.message()).isEqualTo("some error without column info");
    }

    @Test
    void shouldHandleNullInput() {
        var result = CompilationOutputParser.parse(null, PROJECT_DIR);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void shouldHandleEmptyInput() {
        var result = CompilationOutputParser.parse("", PROJECT_DIR);

        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void shouldRelativizeAbsolutePaths() {
        String stdout = "[ERROR] /home/user/my-project/src/main/java/Foo.java:[10,5] error msg";

        var result = CompilationOutputParser.parse(stdout, PROJECT_DIR);

        assertThat(result.errors().getFirst().file()).isEqualTo("src/main/java/Foo.java");
    }

    @Test
    void shouldKeepPathWhenNotUnderProjectRoot() {
        String stdout = "[ERROR] /other/path/Foo.java:[10,5] error msg";

        var result = CompilationOutputParser.parse(stdout, PROJECT_DIR);

        assertThat(result.errors().getFirst().file()).isEqualTo("/other/path/Foo.java");
    }

    private String loadFixture(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load fixture: " + path, e);
        }
    }
}
