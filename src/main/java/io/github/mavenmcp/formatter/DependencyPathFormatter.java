package io.github.mavenmcp.formatter;

import java.util.List;

import io.github.mavenmcp.model.DependencyNode;
import io.github.mavenmcp.model.DependencyPathResult;

/**
 * Formats a {@link DependencyPathResult} as condensed Markdown for LLM consumption.
 */
public final class DependencyPathFormatter {

    private static final int MAX_DISPLAYED_PATHS = 10;

    private DependencyPathFormatter() {
    }

    public static String format(DependencyPathResult result) {
        var sb = new StringBuilder();

        sb.append("# Dependency Path: ").append(result.artifact()).append('\n');

        if (result.resolvedPaths().isEmpty() && result.conflicts().isEmpty()) {
            sb.append("\nNo dependencies matching \"").append(result.artifact()).append("\" found.\n");
            return sb.toString().stripTrailing();
        }

        if (result.resolvedPaths().isEmpty() && !result.conflicts().isEmpty()) {
            sb.append("\nNo resolved dependency found, but conflicts exist.\n");
            appendConflicts(sb, result);
            return sb.toString().stripTrailing();
        }

        int displayed = Math.min(result.resolvedPaths().size(), MAX_DISPLAYED_PATHS);

        if (displayed == 1) {
            // Single match — compact format
            List<DependencyNode> path = result.resolvedPaths().getFirst();
            DependencyNode leaf = path.getLast();
            sb.append("\n**Resolved:** ").append(leaf.gav());
            if (leaf.scope() != null) {
                sb.append(" (").append(leaf.scope()).append(')');
            }
            sb.append("\n\n## Path\n");
            appendTreePath(sb, path);
        } else {
            // Multiple matches
            sb.append("\n**Matches:** ").append(result.totalMatches()).append(" artifacts\n");
            for (int i = 0; i < displayed; i++) {
                List<DependencyNode> path = result.resolvedPaths().get(i);
                DependencyNode leaf = path.getLast();
                sb.append("\n## Path ").append(i + 1).append(": ")
                        .append(leaf.artifactId()).append(':').append(leaf.version());
                if (leaf.scope() != null) {
                    sb.append(" (").append(leaf.scope()).append(')');
                }
                sb.append('\n');
                appendTreePath(sb, path);
            }
        }

        if (result.totalMatches() > MAX_DISPLAYED_PATHS) {
            sb.append("\n> Showing ").append(MAX_DISPLAYED_PATHS)
                    .append(" of ").append(result.totalMatches())
                    .append(" matches. Use a more specific query to narrow results.\n");
        }

        appendConflicts(sb, result);

        return sb.toString().stripTrailing();
    }

    private static void appendTreePath(StringBuilder sb, List<DependencyNode> path) {
        for (int i = 0; i < path.size(); i++) {
            DependencyNode node = path.get(i);
            String indent = "    ".repeat(i);
            String connector = (i == 0) ? "" : "└── ";
            sb.append(indent).append(connector);
            if (i == 0) {
                // Root — show full GAV
                sb.append(node.gav());
            } else if (i == path.size() - 1) {
                // Leaf — show artifactId:version with checkmark
                sb.append(node.artifactId()).append(':').append(node.version()).append(" ✔");
            } else {
                // Intermediate — show full GAV
                sb.append(node.gav());
            }
            sb.append('\n');
        }
    }

    private static void appendConflicts(StringBuilder sb, DependencyPathResult result) {
        if (result.conflicts().isEmpty()) {
            return;
        }
        sb.append("\n## Conflicts (").append(result.conflicts().size()).append(")\n");
        for (var conflict : result.conflicts()) {
            DependencyNode node = conflict.node();
            DependencyNode parent = conflict.parent();
            sb.append("- ").append(node.artifactId()).append(':').append(node.version());
            sb.append(" (from ").append(parent.artifactId()).append(':').append(parent.version()).append(')');
            if (node.omissionReason() != null) {
                sb.append(" — ").append(node.omissionReason());
            }
            sb.append('\n');
        }
    }
}
