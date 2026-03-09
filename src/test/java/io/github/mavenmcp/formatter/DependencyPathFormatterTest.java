package io.github.mavenmcp.formatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.mavenmcp.model.DependencyNode;
import io.github.mavenmcp.model.DependencyPathResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyPathFormatterTest {

    @Test
    void shouldFormatSingleMatchWithNoConflicts() {
        DependencyNode root = node("com.example", "my-app", "1.0.0", null);
        DependencyNode guava = node("com.google.guava", "guava", "33.0.0-jre", "compile");
        DependencyNode failureaccess = node("com.google.guava", "failureaccess", "1.0.2", "compile");

        var result = new DependencyPathResult("failureaccess",
                List.of(List.of(root, guava, failureaccess)),
                Collections.emptyList(), 1);

        String md = DependencyPathFormatter.format(result);

        assertThat(md).contains("# Dependency Path: failureaccess");
        assertThat(md).contains("**Resolved:** com.google.guava:failureaccess:1.0.2 (compile)");
        assertThat(md).contains("## Path");
        assertThat(md).contains("com.example:my-app:1.0.0");
        assertThat(md).contains("└── com.google.guava:guava:33.0.0-jre");
        assertThat(md).contains("└── failureaccess:1.0.2 ✔");
        assertThat(md).doesNotContain("## Conflicts");
    }

    @Test
    void shouldFormatMatchWithConflicts() {
        DependencyNode root = node("com.example", "my-app", "1.0.0", null);
        DependencyNode libA = node("com.example", "lib-a", "1.0.0", "compile");
        DependencyNode jackson = node("com.fasterxml.jackson.core", "jackson-databind", "2.18.3", "compile");
        DependencyNode omittedJackson = new DependencyNode(
                "com.fasterxml.jackson.core", "jackson-databind", "jar", "2.17.0",
                "compile", "omitted for conflict with 2.18.3");
        DependencyNode libB = node("com.example", "lib-b", "2.0.0", "compile");

        var result = new DependencyPathResult("jackson-databind",
                List.of(List.of(root, libA, jackson)),
                List.of(new DependencyPathResult.ConflictEntry(omittedJackson, libB)),
                1);

        String md = DependencyPathFormatter.format(result);

        assertThat(md).contains("**Resolved:** com.fasterxml.jackson.core:jackson-databind:2.18.3 (compile)");
        assertThat(md).contains("## Conflicts (1)");
        assertThat(md).contains("jackson-databind:2.17.0 (from lib-b:2.0.0) — omitted for conflict with 2.18.3");
    }

    @Test
    void shouldFormatNoMatches() {
        var result = new DependencyPathResult("nonexistent",
                Collections.emptyList(), Collections.emptyList(), 0);

        String md = DependencyPathFormatter.format(result);

        assertThat(md).contains("# Dependency Path: nonexistent");
        assertThat(md).contains("No dependencies matching \"nonexistent\" found.");
    }

    @Test
    void shouldFormatMultipleMatches() {
        DependencyNode root = node("com.example", "my-app", "1.0.0", null);
        DependencyNode jackson1 = node("com.fasterxml.jackson.core", "jackson-databind", "2.19.2", "compile");
        DependencyNode jackson2 = node("com.fasterxml.jackson.core", "jackson-core", "2.19.2", "compile");

        var result = new DependencyPathResult("jackson",
                List.of(
                        List.of(root, jackson1),
                        List.of(root, jackson2)
                ),
                Collections.emptyList(), 2);

        String md = DependencyPathFormatter.format(result);

        assertThat(md).contains("**Matches:** 2 artifacts");
        assertThat(md).contains("## Path 1: jackson-databind:2.19.2 (compile)");
        assertThat(md).contains("## Path 2: jackson-core:2.19.2 (compile)");
    }

    @Test
    void shouldShowCapNoteWhenMoreThan10Matches() {
        DependencyNode root = node("com.example", "my-app", "1.0.0", null);
        List<List<DependencyNode>> paths = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DependencyNode dep = node("com.example", "lib-" + i, "1.0." + i, "compile");
            paths.add(List.of(root, dep));
        }

        var result = new DependencyPathResult("lib",
                paths, Collections.emptyList(), 15);

        String md = DependencyPathFormatter.format(result);

        assertThat(md).contains("Showing 10 of 15 matches");
    }

    @Test
    void shouldFormatOmittedOnlyMatch() {
        DependencyNode omitted = new DependencyNode(
                "com.example", "lib-x", "jar", "1.0.0",
                "compile", "omitted for conflict with 2.0.0");
        DependencyNode parent = node("com.example", "parent-lib", "3.0.0", "compile");

        var result = new DependencyPathResult("lib-x",
                Collections.emptyList(),
                List.of(new DependencyPathResult.ConflictEntry(omitted, parent)),
                0);

        String md = DependencyPathFormatter.format(result);

        assertThat(md).contains("No resolved dependency found, but conflicts exist.");
        assertThat(md).contains("## Conflicts (1)");
        assertThat(md).contains("lib-x:1.0.0 (from parent-lib:3.0.0)");
    }

    private static DependencyNode node(String groupId, String artifactId, String version, String scope) {
        return new DependencyNode(groupId, artifactId, "jar", version, scope, null);
    }
}
