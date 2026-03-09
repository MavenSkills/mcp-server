## 1. Model

- [x] 1.1 Create `DependencyNode` record (groupId, artifactId, type, version, scope, omissionReason, children)
- [x] 1.2 Create `DependencyPathResult` record (artifact query, resolvedPaths, conflicts, totalMatches)

## 2. Parser

- [x] 2.1 Implement `DependencyTreeParser.parse(stdout)` — parse `dependency:tree -Dverbose` output into a `DependencyNode` tree
- [x] 2.2 Add parser tests with real-world dependency tree from `/home/mariusz/git/beacon/dependency-tree.txt` (652 lines) and small synthetic cases
- [x] 2.3 Implement path extraction: `findPaths(root, query)` — find all nodes matching substring, return root-to-match paths
- [x] 2.4 Implement conflict collection: gather omitted entries matching the query with parent info and omission reason
- [x] 2.5 Add path extraction and conflict collection tests

## 3. Formatter

- [x] 3.1 Create `DependencyPathFormatter` — format `DependencyPathResult` to condensed Markdown (header, path tree, conflicts section)
- [x] 3.2 Handle edge cases: no matches, multiple matches, result cap (>10), omitted-only matches
- [x] 3.3 Add formatter tests

## 4. Tool

- [x] 4.1 Create `DependencyTraceTool` following `CompileTool` pattern — register `maven_dependency_path` with `artifact` (required) and `args` (optional) parameters
- [x] 4.2 Register tool in `MavenMcpServer`
- [x] 4.3 Add tool integration test
