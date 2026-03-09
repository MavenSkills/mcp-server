package io.github.mavenmcp.parser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.mavenmcp.model.DependencyNode;
import io.github.mavenmcp.model.DependencyPathResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyTreeParserTest {

    // --- parse() tests ---

    @Test
    void shouldParseSimpleTree() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");

        DependencyNode root = DependencyTreeParser.parse(stdout);

        assertThat(root).isNotNull();
        assertThat(root.groupId()).isEqualTo("com.example");
        assertThat(root.artifactId()).isEqualTo("my-app");
        assertThat(root.version()).isEqualTo("1.0.0");
        assertThat(root.children()).hasSize(4); // slf4j, guava, jackson-databind, junit
    }

    @Test
    void shouldParseDirectDependency() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");

        DependencyNode root = DependencyTreeParser.parse(stdout);

        DependencyNode slf4j = root.children().get(0);
        assertThat(slf4j.groupId()).isEqualTo("org.slf4j");
        assertThat(slf4j.artifactId()).isEqualTo("slf4j-api");
        assertThat(slf4j.version()).isEqualTo("2.0.17");
        assertThat(slf4j.scope()).isEqualTo("compile");
        assertThat(slf4j.isOmitted()).isFalse();
    }

    @Test
    void shouldParseTransitiveDependencies() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");

        DependencyNode root = DependencyTreeParser.parse(stdout);

        // guava has 2 children
        DependencyNode guava = root.children().get(1);
        assertThat(guava.artifactId()).isEqualTo("guava");
        assertThat(guava.children()).hasSize(2);
        assertThat(guava.children().get(0).artifactId()).isEqualTo("failureaccess");
        assertThat(guava.children().get(1).artifactId()).isEqualTo("listenablefuture");
    }

    @Test
    void shouldParseDeeplyNestedTransitiveDependencies() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");

        DependencyNode root = DependencyTreeParser.parse(stdout);

        // jackson-databind has 2 children (annotations, core)
        DependencyNode jackson = root.children().get(2);
        assertThat(jackson.artifactId()).isEqualTo("jackson-databind");
        assertThat(jackson.children()).hasSize(2);
        assertThat(jackson.children().get(0).artifactId()).isEqualTo("jackson-annotations");
        assertThat(jackson.children().get(1).artifactId()).isEqualTo("jackson-core");
    }

    @Test
    void shouldParseOmittedConflictEntries() {
        String stdout = loadFixture("dependency-tree/conflicts-tree.txt");

        DependencyNode root = DependencyTreeParser.parse(stdout);

        // lib-b has two omitted children
        DependencyNode libB = root.children().get(1);
        assertThat(libB.artifactId()).isEqualTo("lib-b");
        assertThat(libB.children()).hasSize(2);

        DependencyNode omittedJackson = libB.children().get(0);
        assertThat(omittedJackson.artifactId()).isEqualTo("jackson-databind");
        assertThat(omittedJackson.version()).isEqualTo("2.17.0");
        assertThat(omittedJackson.isOmitted()).isTrue();
        assertThat(omittedJackson.omissionReason()).isEqualTo("omitted for conflict with 2.18.3");
    }

    @Test
    void shouldParseRealWorldBeaconTree() {
        String stdout = loadFixture("dependency-tree/beacon-tree.txt");

        DependencyNode root = DependencyTreeParser.parse(stdout);

        assertThat(root).isNotNull();
        assertThat(root.groupId()).isEqualTo("com.skillpanel");
        assertThat(root.artifactId()).isEqualTo("beacon");
        // Should have direct dependencies
        assertThat(root.children()).isNotEmpty();
    }

    @Test
    void shouldReturnNullForEmptyInput() {
        assertThat(DependencyTreeParser.parse("")).isNull();
        assertThat(DependencyTreeParser.parse(null)).isNull();
    }

    @Test
    void shouldReturnNullForNonTreeOutput() {
        String stdout = """
                [INFO] Scanning for projects...
                [INFO] BUILD SUCCESS
                """;

        DependencyNode root = DependencyTreeParser.parse(stdout);

        assertThat(root).isNull();
    }

    // --- calculateDepth() tests ---

    @Test
    void shouldCalculateDepthZeroForRoot() {
        assertThat(DependencyTreeParser.calculateDepth(
                "com.example:my-app:jar:1.0.0")).isEqualTo(0);
    }

    @Test
    void shouldCalculateDepthOneForDirectDeps() {
        assertThat(DependencyTreeParser.calculateDepth(
                "+- org.slf4j:slf4j-api:jar:2.0.17:compile")).isEqualTo(1);
        assertThat(DependencyTreeParser.calculateDepth(
                "\\- org.junit:junit:jar:5.0:test")).isEqualTo(1);
    }

    @Test
    void shouldCalculateDepthTwoForTransitive() {
        assertThat(DependencyTreeParser.calculateDepth(
                "|  +- com.google:guava:jar:33.0:compile")).isEqualTo(2);
        assertThat(DependencyTreeParser.calculateDepth(
                "|  \\- com.google:guava:jar:33.0:compile")).isEqualTo(2);
    }

    @Test
    void shouldCalculateDepthThreeForDeepTransitive() {
        assertThat(DependencyTreeParser.calculateDepth(
                "|  |  +- org.foo:bar:jar:1.0:compile")).isEqualTo(3);
    }

    // --- parseLine() tests ---

    @Test
    void shouldParseRootLine() {
        DependencyNode node = DependencyTreeParser.parseLine(
                "com.example:my-app:jar:1.0.0");
        assertThat(node).isNotNull();
        assertThat(node.groupId()).isEqualTo("com.example");
        assertThat(node.artifactId()).isEqualTo("my-app");
        assertThat(node.version()).isEqualTo("1.0.0");
        assertThat(node.scope()).isNull();
    }

    @Test
    void shouldParseDirectDepLine() {
        DependencyNode node = DependencyTreeParser.parseLine(
                "+- org.slf4j:slf4j-api:jar:2.0.17:compile");
        assertThat(node).isNotNull();
        assertThat(node.groupId()).isEqualTo("org.slf4j");
        assertThat(node.artifactId()).isEqualTo("slf4j-api");
        assertThat(node.version()).isEqualTo("2.0.17");
        assertThat(node.scope()).isEqualTo("compile");
        assertThat(node.isOmitted()).isFalse();
    }

    @Test
    void shouldParseOmittedLine() {
        DependencyNode node = DependencyTreeParser.parseLine(
                "|  +- (com.fasterxml.jackson.core:jackson-databind:jar:2.17.0:compile - omitted for conflict with 2.18.3)");
        assertThat(node).isNotNull();
        assertThat(node.artifactId()).isEqualTo("jackson-databind");
        assertThat(node.version()).isEqualTo("2.17.0");
        assertThat(node.isOmitted()).isTrue();
        assertThat(node.omissionReason()).isEqualTo("omitted for conflict with 2.18.3");
    }

    @Test
    void shouldParseVersionManagedLine() {
        DependencyNode node = DependencyTreeParser.parseLine(
                "|  +- org.springframework.boot:spring-boot-starter-jackson:jar:4.0.3:compile (version managed from 4.0.3; scope not updated to compile)");
        assertThat(node).isNotNull();
        assertThat(node.artifactId()).isEqualTo("spring-boot-starter-jackson");
        assertThat(node.version()).isEqualTo("4.0.3");
        assertThat(node.omissionReason()).isEqualTo("version managed from 4.0.3; scope not updated to compile");
    }

    @Test
    void shouldParseOmittedDuplicateLine() {
        DependencyNode node = DependencyTreeParser.parseLine(
                "|  |  +- (org.springframework.boot:spring-boot-starter:jar:4.0.3:compile - version managed from 4.0.3; omitted for duplicate)");
        assertThat(node).isNotNull();
        assertThat(node.isOmitted()).isTrue();
        assertThat(node.omissionReason()).isEqualTo("version managed from 4.0.3; omitted for duplicate");
    }

    // --- findPaths() tests ---

    @Test
    void shouldFindDirectDependencyPath() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        List<List<DependencyNode>> paths = DependencyTreeParser.findPaths(root, "picocli");
        // picocli is not in simple tree — use slf4j
        assertThat(paths).isEmpty();

        paths = DependencyTreeParser.findPaths(root, "slf4j-api");
        assertThat(paths).hasSize(1);
        assertThat(paths.getFirst()).hasSize(2); // root → slf4j
        assertThat(paths.getFirst().get(0).artifactId()).isEqualTo("my-app");
        assertThat(paths.getFirst().get(1).artifactId()).isEqualTo("slf4j-api");
    }

    @Test
    void shouldFindTransitiveDependencyPath() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        List<List<DependencyNode>> paths = DependencyTreeParser.findPaths(root, "failureaccess");
        assertThat(paths).hasSize(1);
        assertThat(paths.getFirst()).hasSize(3); // root → guava → failureaccess
        assertThat(paths.getFirst().get(1).artifactId()).isEqualTo("guava");
        assertThat(paths.getFirst().get(2).artifactId()).isEqualTo("failureaccess");
    }

    @Test
    void shouldFindMultipleMatchingPaths() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        // "jackson" matches jackson-databind, jackson-annotations, jackson-core
        List<List<DependencyNode>> paths = DependencyTreeParser.findPaths(root, "jackson");
        assertThat(paths).hasSize(3);
    }

    @Test
    void shouldMatchCaseInsensitively() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        List<List<DependencyNode>> paths = DependencyTreeParser.findPaths(root, "SLF4J-API");
        assertThat(paths).hasSize(1);
        assertThat(paths.getFirst().get(1).artifactId()).isEqualTo("slf4j-api");
    }

    @Test
    void shouldMatchOnGroupId() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        List<List<DependencyNode>> paths = DependencyTreeParser.findPaths(root, "com.google");
        assertThat(paths).hasSize(3); // guava, failureaccess, listenablefuture
    }

    @Test
    void shouldReturnEmptyForNoMatch() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        List<List<DependencyNode>> paths = DependencyTreeParser.findPaths(root, "nonexistent");
        assertThat(paths).isEmpty();
    }

    @Test
    void shouldNotIncludeOmittedNodesInPaths() {
        String stdout = loadFixture("dependency-tree/conflicts-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        // jackson-databind has 1 resolved + 2 omitted entries
        List<List<DependencyNode>> paths = DependencyTreeParser.findPaths(root, "jackson-databind");
        assertThat(paths).hasSize(1); // only the resolved one
        assertThat(paths.getFirst().get(2).version()).isEqualTo("2.18.3");
    }

    // --- findConflicts() tests ---

    @Test
    void shouldFindConflictsForMatchingArtifact() {
        String stdout = loadFixture("dependency-tree/conflicts-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        List<DependencyPathResult.ConflictEntry> conflicts =
                DependencyTreeParser.findConflicts(root, "jackson-databind");
        assertThat(conflicts).hasSize(2); // omitted from lib-b and lib-c
        assertThat(conflicts.get(0).node().version()).isEqualTo("2.17.0");
        assertThat(conflicts.get(0).parent().artifactId()).isEqualTo("lib-b");
        assertThat(conflicts.get(1).node().version()).isEqualTo("2.19.0");
        assertThat(conflicts.get(1).parent().artifactId()).isEqualTo("lib-c");
    }

    @Test
    void shouldReturnEmptyConflictsWhenNoneExist() {
        String stdout = loadFixture("dependency-tree/simple-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        List<DependencyPathResult.ConflictEntry> conflicts =
                DependencyTreeParser.findConflicts(root, "guava");
        assertThat(conflicts).isEmpty();
    }

    @Test
    void shouldFindConflictsInRealWorldTree() {
        String stdout = loadFixture("dependency-tree/beacon-tree.txt");
        DependencyNode root = DependencyTreeParser.parse(stdout);

        // friendly-id has a known conflict in the beacon tree
        List<DependencyPathResult.ConflictEntry> conflicts =
                DependencyTreeParser.findConflicts(root, "friendly-id");
        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts).anyMatch(c ->
                c.node().omissionReason() != null
                && c.node().omissionReason().contains("omitted for conflict"));
    }

    private String loadFixture(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load fixture: " + path, e);
        }
    }
}
