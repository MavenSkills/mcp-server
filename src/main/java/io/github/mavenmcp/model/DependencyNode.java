package io.github.mavenmcp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in the Maven dependency tree.
 *
 * <p>Represents a single dependency (or the root project) with its coordinates,
 * optional omission reason, and child dependencies.</p>
 */
public final class DependencyNode {

    private final String groupId;
    private final String artifactId;
    private final String type;
    private final String version;
    private final String scope;
    private final String omissionReason;
    private final List<DependencyNode> children = new ArrayList<>();

    public DependencyNode(String groupId, String artifactId, String type,
                          String version, String scope, String omissionReason) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.type = type;
        this.version = version;
        this.scope = scope;
        this.omissionReason = omissionReason;
    }

    public String groupId() { return groupId; }
    public String artifactId() { return artifactId; }
    public String type() { return type; }
    public String version() { return version; }
    public String scope() { return scope; }
    public String omissionReason() { return omissionReason; }
    public List<DependencyNode> children() { return children; }

    public boolean isOmitted() {
        return omissionReason != null;
    }

    /**
     * Returns groupId:artifactId for matching purposes.
     */
    public String coordinate() {
        return groupId + ":" + artifactId;
    }

    /**
     * Returns groupId:artifactId:version for display.
     */
    public String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
