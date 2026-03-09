## ADDED Requirements

### Requirement: MCP tool maven_dependency_path
The server SHALL register an MCP tool named `maven_dependency_path` with description "Trace where a dependency comes from in the Maven dependency tree. Returns the path from project root to matching artifacts and any version conflicts." The tool SHALL invoke `mvn dependency:tree -Dverbose -B` via MavenRunner and return a Markdown-formatted result.

#### Scenario: Single matching dependency
- **WHEN** the agent calls `maven_dependency_path` with `artifact: "picocli"`
- **THEN** the tool SHALL return Markdown showing the resolved artifact and its path from the project root

#### Scenario: No matching dependency
- **WHEN** the agent calls `maven_dependency_path` with `artifact: "nonexistent-lib"`
- **THEN** the tool SHALL return Markdown stating no dependencies matching "nonexistent-lib" were found

#### Scenario: Maven execution failure
- **WHEN** `dependency:tree` execution fails (e.g., invalid POM)
- **THEN** the tool SHALL return a `CallToolResult` with `isError=true` and an error message

### Requirement: maven_dependency_path artifact parameter
The tool SHALL accept a required `artifact` parameter (string) that is used for case-insensitive substring matching against the `groupId:artifactId` of each dependency in the tree. The tool SHALL NOT match against version, scope, or type.

#### Scenario: Substring match on artifactId
- **WHEN** `artifact` is `"friendly-id"` and the tree contains `com.devskiller.friendly-id:friendly-id:jar:1.1.0:compile`
- **THEN** the artifact SHALL match

#### Scenario: Substring match on groupId
- **WHEN** `artifact` is `"devskiller"` and the tree contains `com.devskiller.friendly-id:friendly-id:jar:1.1.0:compile`
- **THEN** the artifact SHALL match

#### Scenario: Case-insensitive matching
- **WHEN** `artifact` is `"JUnit"` and the tree contains `org.junit.jupiter:junit-jupiter:jar:5.11.4:test`
- **THEN** the artifact SHALL match

### Requirement: maven_dependency_path accepts additional Maven arguments
The tool SHALL accept an optional `args` parameter (array of strings) that SHALL be appended to the Maven command line.

#### Scenario: Extra arguments passed
- **WHEN** the agent calls `maven_dependency_path` with `args: ["-pl", "submodule"]`
- **THEN** the server SHALL execute `<maven> dependency:tree -Dverbose -B -pl submodule`

#### Scenario: No extra arguments
- **WHEN** the agent calls `maven_dependency_path` without the `args` parameter
- **THEN** the server SHALL execute `<maven> dependency:tree -Dverbose -B`

### Requirement: Parse dependency tree into structured tree
The `DependencyTreeParser` SHALL parse `dependency:tree -Dverbose` stdout into a tree of `DependencyNode` records. Each node SHALL contain: `groupId`, `artifactId`, `type`, `version`, `scope`, `omissionReason` (nullable), and a list of `children`.

#### Scenario: Parse root node
- **WHEN** stdout contains `[INFO] io.github.mavenskills:maven-mcp:jar:1.0.0-SNAPSHOT`
- **THEN** the parser SHALL return a root node with groupId=`io.github.mavenskills`, artifactId=`maven-mcp`, version=`1.0.0-SNAPSHOT`

#### Scenario: Parse direct dependency
- **WHEN** stdout contains `[INFO] +- info.picocli:picocli:jar:4.7.7:compile`
- **THEN** the parser SHALL add a child node to the root with groupId=`info.picocli`, artifactId=`picocli`, version=`4.7.7`, scope=`compile`

#### Scenario: Parse transitive dependency
- **WHEN** stdout contains a dependency at indent level 2 (e.g., `[INFO] |  +- com.ethlo.time:itu:jar:1.14.0:compile`)
- **THEN** the parser SHALL add it as a child of the level-1 parent above it

#### Scenario: Parse omitted dependency
- **WHEN** stdout contains `[INFO] |  +- (com.fasterxml.jackson.core:jackson-databind:jar:2.18.3:compile - omitted for conflict with 2.19.2)`
- **THEN** the parser SHALL create a node with omissionReason=`omitted for conflict with 2.19.2`

#### Scenario: Parse version-managed dependency
- **WHEN** stdout contains a line with `version managed from X.Y.Z`
- **THEN** the parser SHALL capture this in the node's omissionReason

### Requirement: Extract dependency paths to matching artifacts
Given a parsed dependency tree and a query string, the tool SHALL find all nodes whose `groupId:artifactId` contains the query (case-insensitive) and return the full path from root to each match.

#### Scenario: Direct dependency match
- **WHEN** query is `"picocli"` and `picocli` is a direct dependency
- **THEN** the result SHALL contain one path of length 2: `[root, picocli]`

#### Scenario: Transitive dependency match
- **WHEN** query is `"itu"` and `itu` is a transitive dependency under `json-schema-validator` under `mcp-json-jackson2` under `mcp`
- **THEN** the result SHALL contain one path of length 5: `[root, mcp, mcp-json-jackson2, json-schema-validator, itu]`

#### Scenario: Multiple matches
- **WHEN** query matches 3 artifacts in the tree
- **THEN** the result SHALL contain 3 separate paths

### Requirement: Collect conflicts for matched artifacts
The tool SHALL collect all omitted/conflicted entries matching the query and report them separately from resolved paths. A conflict entry SHALL include the omitted version, its parent artifact, and the omission reason.

#### Scenario: Dependency with conflict
- **WHEN** query is `"jackson-databind"` and the tree contains both a resolved `jackson-databind:2.19.2` and omitted `jackson-databind:2.18.3 - omitted for conflict with 2.19.2`
- **THEN** the result SHALL contain the resolved path AND a conflict entry showing the omitted version with reason

#### Scenario: No conflicts
- **WHEN** query matches only resolved (non-omitted) artifacts
- **THEN** the conflicts list SHALL be empty

### Requirement: Result cap at 10 matches
When the query matches more than 10 resolved artifacts, the tool SHALL return only the first 10 paths and include a note indicating the total number of matches.

#### Scenario: Exactly 10 matches
- **WHEN** query matches exactly 10 artifacts
- **THEN** all 10 paths SHALL be included with no truncation note

#### Scenario: More than 10 matches
- **WHEN** query matches 15 artifacts
- **THEN** only 10 paths SHALL be included and the output SHALL contain a note: "Showing 10 of 15 matches. Use a more specific query to narrow results."

### Requirement: Markdown output format
The tool SHALL return a Markdown-formatted response optimized for LLM consumption. The format SHALL include: a header with the query, the resolved artifact coordinates, a tree-style path visualization, and a conflicts section (if any).

#### Scenario: Single match with no conflicts
- **WHEN** one artifact matches and has no conflicts
- **THEN** the Markdown SHALL contain a `# Dependency Path:` header, a `**Resolved:**` line, a `## Path` section with tree visualization, and no conflicts section

#### Scenario: Match with conflicts
- **WHEN** one artifact matches and has conflicts
- **THEN** the Markdown SHALL additionally contain a `## Conflicts` section listing each conflicted version with its parent and reason

#### Scenario: Multiple matches
- **WHEN** 3 artifacts match
- **THEN** each match SHALL appear as a separate numbered path section
