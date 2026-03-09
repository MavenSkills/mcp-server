## Context

The Maven MCP server currently provides `maven_compile`, `maven_test`, and `maven_clean` tools. All follow the same pattern: run a Maven goal via `MavenRunner`, parse stdout with a dedicated parser, build a model, and format to Markdown via `MarkdownFormatter`.

Dependency tracing is a common task when investigating version conflicts, unexpected transitive dependencies, or BOM management. The raw `dependency:tree -Dverbose` output can be hundreds of lines and requires manual grep/sed to extract useful information.

## Goals / Non-Goals

**Goals:**
- Add `maven_dependency_path` tool that answers "where does artifact X come from?"
- Parse `dependency:tree -Dverbose` output into a tree structure
- Extract and display only the path(s) from root to matching artifact(s)
- Surface version conflicts and omissions for matched artifacts
- Keep output condensed — similar token savings as compile/test tools

**Non-Goals:**
- Full dependency tree visualization (that's just raw `dependency:tree`)
- Dependency version management or resolution suggestions
- Multi-module project support (single POM scope, same as other tools)
- Scope filtering parameter (can be added later)

## Decisions

### 1. Tool name: `maven_dependency_path`

Name communicates the purpose — finding the path to a dependency. Alternatives considered:
- `maven_dependency_tree` — too generic, implies full tree dump
- `maven_dependency_trace` — good but "path" is more intuitive

### 2. Substring matching on `groupId:artifactId`

The `artifact` parameter uses case-insensitive substring matching against the `groupId:artifactId` portion of each dependency. This matches how developers naturally search (e.g., `friendly-id`, `jackson-core`, `slf4j`).

No version matching — the goal is to find where an artifact lives in the tree, regardless of version.

### 3. Parser architecture: line-by-line with indent tracking

The `dependency:tree` output uses a consistent indentation scheme with `|`, `+- `, and `\- ` markers. Each indent level is 3 characters wide. The parser will:

1. Strip the `[INFO] ` prefix from each line
2. Calculate depth from the indentation pattern
3. Build a tree of `DependencyNode` objects, each with: `groupId`, `artifactId`, `type`, `version`, `scope`, `omissionReason` (nullable), and `children`
4. Omitted/conflicted entries (lines containing `(` and `)`) are parsed with their reason extracted

### 4. Output model: `DependencyPathResult`

A dedicated record containing:
- `artifact`: the query string
- `resolvedPaths`: list of paths (each path = list of `DependencyNode` from root to match)
- `conflicts`: list of omitted entries matching the query with their omission reason
- `totalMatches`: count of matched artifacts

### 5. Markdown formatting: inline in tool or dedicated formatter

Since the output structure is unique (tree paths, not build errors), use a dedicated `DependencyPathFormatter` rather than extending `MarkdownFormatter`. The output format:

```markdown
# Dependency Path: friendly-id

**Resolved:** com.devskiller.friendly-id:friendly-id:1.1.0 (compile)

## Path
maven-mcp:1.0.0-SNAPSHOT
└── com.devskiller:toolkit:1.3-38
    └── friendly-id:1.1.0 ✔

## Conflicts (1)
- friendly-id:2.0.0-SNAPSHOT (from friendly-id-jackson-datatype:2.0.0-SNAPSHOT) — omitted for conflict with 1.1.0
```

When multiple artifacts match, show each path under a numbered section.

When no matches found:
```markdown
# Dependency Path: nonexistent-lib

No dependencies matching "nonexistent-lib" found.
```

### 6. Result cap

If the query matches more than 10 artifacts, show only the first 10 paths and add a note: "Showing 10 of N matches. Use a more specific query to narrow results."

## Risks / Trade-offs

- **[Performance]** `dependency:tree -Dverbose` can be slow on large projects (downloads metadata) → No mitigation needed, same as other tools — Maven execution time is inherent
- **[Parsing fragility]** Tree format could vary between Maven versions → The `|  +- ` format has been stable across Maven 3.x and 4.x; parser tests will cover edge cases
- **[Ambiguous matches]** Short queries like `jackson` may match many artifacts → Result cap at 10 with guidance to narrow the query
