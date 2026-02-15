## Context

`maven_test` currently always runs `mvn test`, which triggers the full Maven lifecycle (`validate` → `generate-sources` → `compile` → `test-compile` → `test`). For projects with expensive `generate-sources` plugins (jOOQ + Testcontainers, protobuf, JAXB), this adds significant overhead even when code is already compiled.

The `MavenRunner.execute(goal, extraArgs, mavenExecutable, projectDir)` method already accepts any goal string. The `TestTool.create()` method currently hardcodes `"test"` as the goal. All other test processing logic (Surefire XML parsing, stack trace filtering, deduplication) is goal-independent and works the same way for both `test` and `surefire:test`.

## Goals / Non-Goals

**Goals:**
- Allow skipping lifecycle phases when code is already compiled, by running `surefire:test` directly
- Prevent confusing failures when `surefire:test` is invoked without prior compilation
- Preserve full backward compatibility — default behavior unchanged

**Non-Goals:**
- Detecting whether recompilation is *needed* (stale class files vs. modified sources) — this would require tracking source timestamps, which Maven itself handles
- Supporting Failsafe (`integration-test` / `failsafe:integration-test`) — separate concern, separate tool if needed
- Auto-selecting `testOnly` mode — the caller (Claude / user) decides explicitly

## Decisions

### 1. Goal selection: `"test"` vs `"surefire:test"`

**Decision:** When `testOnly=true`, pass `"surefire:test"` as the goal to `MavenRunner.execute()`.

**Rationale:** `surefire:test` is the direct plugin goal — it runs only the Surefire plugin without triggering any lifecycle phase. This is exactly the Maven-native way to "just run tests". No alternative was seriously considered because this is the standard Maven mechanism.

### 2. Pre-flight guard: check `target/test-classes`

**Decision:** Before executing `surefire:test`, check `config.projectDir().resolve("target/test-classes")` exists via `Files.isDirectory()`. If missing, return an error `CallToolResult` with message: *"Project not compiled. Run maven_compile first or set testOnly=false."*

**Rationale:** Running `surefire:test` without compiled test classes produces a cryptic Maven error. The guard gives a clear, actionable message. Checking `target/test-classes` (not `target/classes`) is sufficient — if test classes exist, main classes were compiled too (Maven compiles main before test).

**Alternative considered:** Check both `target/classes` and `target/test-classes` — rejected as redundant; `test-classes` implies `classes` was already run.

### 3. Parameter extraction pattern

**Decision:** Use `ToolUtils.extractBoolean(params, "testOnly", false)` — consistent with existing `includeTestLogs` extraction.

**Rationale:** Follows established code patterns in `TestTool.create()`. Default `false` preserves backward compatibility.

### 4. Guard placement

**Decision:** Place the guard check inside the tool lambda in `create()`, after parameter extraction and before `runner.execute()`. Return early with `isError=true` if the check fails.

**Rationale:** Keeps the guard close to the execution call. The error response follows the same `CallToolResult(List.of(new TextContent(...)), true)` pattern used for other error cases in the method.

## Risks / Trade-offs

- **[Stale classes]** → User runs `testOnly=true` after modifying source but not recompiling — tests pass against old bytecode. **Mitigation:** This is documented behavior (user opted in). The guard only catches "never compiled", not "stale compilation". This matches how `surefire:test` works natively in Maven.
- **[No Surefire plugin]** → Exotic projects without Surefire would fail on `surefire:test`. **Mitigation:** Extremely unlikely — Surefire is the default test plugin. Error message from Maven would be clear enough.
- **[Multi-module projects]** → `surefire:test` in a multi-module reactor only runs in the current module. **Mitigation:** Out of scope — the MCP server already targets single-module projects only (per SPEC.md).
