## Why

Tracing where a transitive dependency comes from in a Maven project requires running `dependency:tree -Dverbose` and manually parsing hundreds of lines of indented output — typically 4-5 commands to grep, filter, and follow the tree. AI agents performing dependency analysis waste significant tokens on raw tree output. A dedicated tool that answers "where does artifact X come from?" with a condensed, structured response would save time for both humans and agents.

## What Changes

- Add a new MCP tool `maven_dependency_path` that traces the origin of a specific dependency
- Add a `DependencyTreeParser` that parses `dependency:tree -Dverbose` output into a tree structure
- Extract dependency paths from root to matching artifacts, including conflict information
- Format results as condensed Markdown (similar to existing compile/test output)

## Capabilities

### New Capabilities
- `dependency-trace`: Traces the origin of a dependency in the Maven dependency tree. Accepts an artifact query (substring match on groupId:artifactId), runs `dependency:tree -Dverbose`, parses the output, and returns condensed Markdown showing the path(s) from project root to matching artifacts plus any version conflicts.

### Modified Capabilities

## Impact

- New files: `DependencyTraceTool.java`, `DependencyTreeParser.java`, model class(es) for parsed results
- `MavenMcpServer.java`: register the new tool
- `MarkdownFormatter.java`: may need extension or a new formatter for dependency trace output
- No breaking changes to existing tools or APIs
