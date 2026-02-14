package io.github.mavenmcp.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MavenOutputFilterTest {

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(MavenOutputFilter.filter(null)).isNull();
    }

    @Test
    void shouldPreserveErrorLines() {
        String input = """
                [INFO] Compiling 42 source files
                [ERROR] /path/Foo.java:[10,5] cannot find symbol
                [ERROR] BUILD FAILURE
                [INFO] Finished at 2024-01-01""";

        String result = MavenOutputFilter.filter(input);

        assertThat(result).contains("[ERROR] /path/Foo.java:[10,5] cannot find symbol");
        assertThat(result).contains("[ERROR] BUILD FAILURE");
    }

    @Test
    void shouldPreserveWarningLines() {
        String input = """
                [INFO] Scanning for projects
                [WARNING] Using platform encoding UTF-8
                [INFO] Building project""";

        String result = MavenOutputFilter.filter(input);

        assertThat(result).contains("[WARNING] Using platform encoding UTF-8");
        assertThat(result).doesNotContain("Scanning for projects");
    }

    @Test
    void shouldDropGenericInfoLines() {
        String input = """
                [INFO] Scanning for projects
                [INFO] Compiling 42 source files to /path/target/classes
                [INFO] Nothing to compile - all classes are up to date""";

        String result = MavenOutputFilter.filter(input);

        assertThat(result).isNull();
    }

    @Test
    void shouldKeepInfoLineWithBuildFailure() {
        String input = "[INFO] BUILD FAILURE";

        String result = MavenOutputFilter.filter(input);

        assertThat(result).isEqualTo("[INFO] BUILD FAILURE");
    }

    @Test
    void shouldDropDownloadLines() {
        String input = """
                Downloading: https://repo.maven.apache.org/maven2/org/example/lib/1.0/lib-1.0.jar
                Downloaded: https://repo.maven.apache.org/maven2/org/example/lib/1.0/lib-1.0.jar (45 kB at 1.2 MB/s)
                Progress (1): 45 kB
                [ERROR] Build failed""";

        String result = MavenOutputFilter.filter(input);

        assertThat(result).doesNotContain("Downloading:");
        assertThat(result).doesNotContain("Downloaded:");
        assertThat(result).doesNotContain("Progress (");
        assertThat(result).contains("[ERROR] Build failed");
    }

    @Test
    void shouldDropPluginBanners() {
        String input = """
                [INFO] --- maven-compiler-plugin:3.11.0:compile (default-compile) @ my-project ---
                [INFO] --- maven-surefire-plugin:3.2.5:test (default-test) @ my-project ---
                [ERROR] Tests failed""";

        String result = MavenOutputFilter.filter(input);

        assertThat(result).doesNotContain("maven-compiler-plugin");
        assertThat(result).doesNotContain("maven-surefire-plugin");
        assertThat(result).contains("[ERROR] Tests failed");
    }

    @Test
    void shouldDropBlankLines() {
        String input = "[ERROR] Fail\n\n\n[ERROR] Another\n\n";

        String result = MavenOutputFilter.filter(input);

        assertThat(result.lines().count()).isEqualTo(2);
    }

    @Test
    void shouldPreserveBuildFailureBlock() {
        String input = """
                [INFO] Compiling sources
                [INFO] BUILD FAILURE
                [ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile
                [ERROR] -> [Help 1]""";

        String result = MavenOutputFilter.filter(input);

        assertThat(result).contains("[INFO] BUILD FAILURE");
        assertThat(result).contains("Failed to execute goal");
        assertThat(result).contains("[Help 1]");
    }

    @Test
    void shouldPreserveTestSummaryLines() {
        String input = """
                [INFO] Running tests
                [ERROR] Tests run: 10, Failures: 2, Errors: 1, Skipped: 0
                [ERROR] Failed tests:
                [ERROR]   com.example.FooTest.shouldWork
                [ERROR] Tests in error:
                [ERROR]   com.example.BarTest.shouldNotThrow""";

        String result = MavenOutputFilter.filter(input);

        assertThat(result).contains("Tests run: 10, Failures: 2");
        assertThat(result).contains("Failed tests:");
        assertThat(result).contains("com.example.FooTest.shouldWork");
        assertThat(result).contains("Tests in error:");
    }

    @Test
    void shouldPreserveReactorSummary() {
        String input = """
                [INFO] Reactor Summary:
                [INFO] module-a ........... SUCCESS
                [INFO] module-b ........... FAILURE""";

        String result = MavenOutputFilter.filter(input);

        assertThat(result).contains("Reactor Summary");
    }

    @Test
    void shouldPreserveLineOrder() {
        String input = """
                [INFO] Scanning
                [WARNING] line A
                [INFO] Compiling
                [ERROR] line B
                [INFO] Nothing
                [ERROR] line C""";

        String result = MavenOutputFilter.filter(input);

        String[] lines = result.split("\n");
        assertThat(lines[0]).contains("[WARNING] line A");
        assertThat(lines[1]).contains("[ERROR] line B");
        assertThat(lines[2]).contains("[ERROR] line C");
    }
}
