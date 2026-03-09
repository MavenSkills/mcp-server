package io.github.mavenmcp.model;

import java.util.List;

/**
 * Result of tracing a dependency in the Maven dependency tree.
 *
 * @param artifact      the search query string
 * @param resolvedPaths list of paths from root to each matching resolved artifact
 * @param conflicts     list of omitted/conflicted entries matching the query
 * @param totalMatches  total count of matching resolved artifacts (before cap)
 */
public record DependencyPathResult(
        String artifact,
        List<List<DependencyNode>> resolvedPaths,
        List<ConflictEntry> conflicts,
        int totalMatches
) {

    /**
     * An omitted dependency entry with context about why it was excluded.
     *
     * @param node   the omitted dependency node
     * @param parent the parent node that declared this dependency
     */
    public record ConflictEntry(DependencyNode node, DependencyNode parent) {
    }
}
