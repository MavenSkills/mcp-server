package io.github.mavenmcp.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.mavenmcp.model.DependencyNode;
import io.github.mavenmcp.model.DependencyPathResult;

/**
 * Parses Maven {@code dependency:tree -Dverbose} output into a {@link DependencyNode} tree.
 */
public final class DependencyTreeParser {

    // Matches lines like: [INFO] +- group:artifact:type:version:scope
    // or omitted: [INFO] |  +- (group:artifact:type:version:scope - reason)
    // The tree prefix uses |, +-, \- characters with 3-char indent per level
    private static final Pattern INFO_LINE = Pattern.compile("^\\[INFO] (.*)$");

    // Matches dependency coordinates: group:artifact:type:version:scope
    // Optionally wrapped in parentheses (omitted entries) with a trailing reason
    private static final Pattern DEPENDENCY_COORDS = Pattern.compile(
            "\\(?([^:]+):([^:]+):([^:]+):([^:]+):([^)\\s]+)"
            + "(?:\\s*-\\s*(.+?))?\\)?\\s*(?:\\((.+?)\\))?\\s*$"
    );

    // Matches root project line: group:artifact:type:version (no scope)
    private static final Pattern ROOT_COORDS = Pattern.compile(
            "^([^:]+):([^:]+):([^:]+):([^:]+)$"
    );

    private DependencyTreeParser() {
    }

    /**
     * Parse dependency:tree -Dverbose output into a tree of DependencyNode.
     *
     * @param stdout the full stdout from Maven dependency:tree -Dverbose
     * @return the root DependencyNode, or null if no tree found
     */
    public static DependencyNode parse(String stdout) {
        if (stdout == null || stdout.isEmpty()) {
            return null;
        }

        DependencyNode root = null;
        // Stack tracks (depth, node) to build the tree
        Deque<DepthNode> stack = new ArrayDeque<>();

        for (String rawLine : stdout.split("\n")) {
            Matcher infoMatcher = INFO_LINE.matcher(rawLine);
            if (!infoMatcher.matches()) {
                continue;
            }

            String content = infoMatcher.group(1);

            // Skip non-tree lines (separators, blank lines, build info)
            if (content.isBlank()
                    || content.startsWith("---")
                    || content.startsWith("Building ")
                    || content.startsWith("  from ")
                    || content.contains("------")
                    || content.startsWith("BUILD ")
                    || content.startsWith("Total time")
                    || content.startsWith("Finished at")
                    || content.startsWith("Scanning")) {
                continue;
            }

            DependencyNode node = parseLine(content);
            if (node == null) {
                continue;
            }

            int depth = calculateDepth(content);

            if (root == null) {
                root = node;
                stack.push(new DepthNode(0, root));
            } else {
                // Pop stack until we find the parent (depth - 1)
                while (!stack.isEmpty() && stack.peek().depth >= depth) {
                    stack.pop();
                }
                if (!stack.isEmpty()) {
                    stack.peek().node.children().add(node);
                }
                stack.push(new DepthNode(depth, node));
            }
        }

        return root;
    }

    static DependencyNode parseLine(String content) {
        // Strip tree drawing characters to get the coordinate part
        String stripped = stripTreePrefix(content);
        if (stripped.isEmpty()) {
            return null;
        }

        // Try root line first (group:artifact:type:version — no scope)
        Matcher rootMatcher = ROOT_COORDS.matcher(stripped);
        if (rootMatcher.matches()) {
            return new DependencyNode(
                    rootMatcher.group(1), rootMatcher.group(2),
                    rootMatcher.group(3), rootMatcher.group(4),
                    null, null);
        }

        // Check if this is an omitted entry (wrapped in parentheses)
        boolean isOmitted = stripped.startsWith("(");

        // Try to match dependency coordinates
        Matcher m = DEPENDENCY_COORDS.matcher(stripped);
        if (!m.find()) {
            return null;
        }

        String groupId = m.group(1);
        String artifactId = m.group(2);
        String type = m.group(3);
        String version = m.group(4);
        String scope = m.group(5);

        // Omission reason can come from inside parens or from annotation after coords
        String omissionReason = null;
        if (isOmitted && m.group(6) != null) {
            omissionReason = m.group(6).trim();
        }
        // Also check for annotations like "(version managed from X.Y.Z)"
        if (m.group(7) != null) {
            String annotation = m.group(7).trim();
            if (!annotation.isEmpty()) {
                omissionReason = annotation;
            }
        }

        return new DependencyNode(groupId, artifactId, type, version, scope, omissionReason);
    }

    static int calculateDepth(String content) {
        // Root has no tree prefix → depth 0
        // Direct deps start with "+- " or "\- " → depth 1
        // Each additional level adds "|  " or "   " (3 chars) of prefix
        int idx = 0;
        int depth = 0;

        while (idx < content.length()) {
            char c = content.charAt(idx);
            if (c == '+' || c == '\\') {
                depth++;
                break;
            } else if (c == '|' || c == ' ') {
                idx++;
                // Each level is 3 characters: "|  " or "   "
                if (idx < content.length() && (content.charAt(idx) == ' ')) {
                    idx++; // skip second char
                    if (idx < content.length() && (content.charAt(idx) == ' ')) {
                        idx++; // skip third char
                    }
                }
                depth++;
            } else {
                // Hit a non-tree character (start of coordinates)
                break;
            }
        }

        return depth;
    }

    /**
     * Find all paths from root to nodes matching the query (case-insensitive substring on groupId:artifactId).
     * Only returns paths to resolved (non-omitted) nodes.
     *
     * @param root  the root of the dependency tree
     * @param query the search string
     * @return list of paths, each path is a list from root to matched node
     */
    public static List<List<DependencyNode>> findPaths(DependencyNode root, String query) {
        List<List<DependencyNode>> result = new ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<DependencyNode> currentPath = new ArrayList<>();
        currentPath.add(root);
        findPathsRecursive(root, lowerQuery, currentPath, result);
        return result;
    }

    private static void findPathsRecursive(DependencyNode node, String lowerQuery,
                                            List<DependencyNode> currentPath,
                                            List<List<DependencyNode>> result) {
        for (DependencyNode child : node.children()) {
            currentPath.add(child);
            if (!child.isOmitted() && matches(child, lowerQuery)) {
                result.add(new ArrayList<>(currentPath));
            }
            findPathsRecursive(child, lowerQuery, currentPath, result);
            currentPath.removeLast();
        }
    }

    /**
     * Find all omitted/conflicted entries matching the query, with their parent context.
     *
     * @param root  the root of the dependency tree
     * @param query the search string
     * @return list of conflict entries
     */
    public static List<DependencyPathResult.ConflictEntry> findConflicts(DependencyNode root, String query) {
        List<DependencyPathResult.ConflictEntry> result = new ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        findConflictsRecursive(root, lowerQuery, result);
        return result;
    }

    private static void findConflictsRecursive(DependencyNode parent, String lowerQuery,
                                                List<DependencyPathResult.ConflictEntry> result) {
        for (DependencyNode child : parent.children()) {
            if (child.isOmitted() && matches(child, lowerQuery)) {
                result.add(new DependencyPathResult.ConflictEntry(child, parent));
            }
            findConflictsRecursive(child, lowerQuery, result);
        }
    }

    private static boolean matches(DependencyNode node, String lowerQuery) {
        return node.coordinate().toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private static String stripTreePrefix(String content) {
        // Remove leading tree-drawing characters: |, +, \, -, space
        int i = 0;
        while (i < content.length()) {
            char c = content.charAt(i);
            if (c == '|' || c == '+' || c == '\\' || c == '-' || c == ' ') {
                i++;
            } else {
                break;
            }
        }
        return content.substring(i).trim();
    }

    private record DepthNode(int depth, DependencyNode node) {
    }
}
