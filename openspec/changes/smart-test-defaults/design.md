## Context

The `testOnly` parameter (added in the previous change) defaults to `false`, causing a full Maven lifecycle on every `maven_test` call. Benchmarks show `testOnly=true` saves 10-13s (36% faster) by running `surefire:test` directly. In practice, code is already compiled in the vast majority of test invocations.

The existing pre-flight guard catches the "never compiled" case (`target/test-classes` missing), but there is no detection for "compiled but stale" — when sources have been modified since the last compilation. This leads to tests passing against outdated bytecode, which is confusing.

`BuildResult` is a record with `@JsonInclude(NON_NULL)` — nullable fields are omitted from JSON. Currently has no field for non-error warnings.

`maven_compile` runs goal `"compile"` which produces `target/classes/` but NOT `target/test-classes/`. Test classes are only created by `test-compile` (part of the `mvn test` lifecycle). This means after `maven_compile`, the existing guard will still block `surefire:test` until at least one full `maven_test` (with `testOnly=false`) has run.

## Goals / Non-Goals

**Goals:**
- Make `testOnly=true` the default for faster test feedback in the common case
- Detect stale classes via timestamp heuristic and auto-recompile using direct plugin goals (`compiler:compile compiler:testCompile`), avoiding expensive lifecycle phases like `generate-sources`
- Always inform the caller that `testOnly` mode was used and what happened (skipped compilation, auto-recompiled, etc.)

**Non-Goals:**
- Precise file-level staleness tracking (source→class mapping) — Maven's incremental compiler handles this
- Detecting staleness in generated sources (`target/generated-sources/`) — too project-specific
- Changing `maven_compile` to also run `test-compile` — separate concern, separate change
- Stale detection when `testOnly=false` — Maven lifecycle handles recompilation
- Auto-recompile when `generate-sources` output is stale — this requires a full lifecycle, use `testOnly=false`

## Decisions

### 1. Default value change: `false` → `true` with proactive guidance

**Decision:** Change `ToolUtils.extractBoolean(params, "testOnly", false)` to `ToolUtils.extractBoolean(params, "testOnly", true)` and update the `INPUT_SCHEMA` description to reflect the new default and include guidance for LLM callers about when to use `false`.

New `testOnly` description in `INPUT_SCHEMA`: `"Default: true (skips lifecycle, runs surefire:test directly with auto-recompile). Set to false when changes go beyond Java source code — e.g., build config (pom.xml), generated source templates, new dependencies, or resource files that affect compilation."`

**Rationale:** In the typical workflow (compile → test → re-test), code is already compiled. The existing pre-flight guard provides a clear error when classes are missing, so the failure mode is safe and actionable. The proactive guidance in the description helps LLM callers (like Claude) choose `testOnly=false` upfront when they know the changes require a full lifecycle, avoiding a wasted round-trip (fail → read note → retry).

**Alternative considered:** Auto-detect whether to use `testOnly` based on `target/test-classes` existence — rejected as implicit magic. Explicit default with clear override is more predictable.

### 2. Stale-classes detection approach: timestamp heuristic

**Decision:** Use `Files.walk()` to find the newest `.java` file timestamp under `src/` and the newest `.class` file timestamp under `target/classes/`. If the newest source is newer than the newest class, attach a warning to the response.

**Rationale:** Simple, fast (~1-5ms for single-module projects), no external dependencies. Comparing the two maximums is a conservative heuristic — if *any* source is newer than the latest compilation output, something may be stale. False positives are possible (e.g., editing a comment), but the cost of a false positive (an extra warning line) is negligible.

**Alternative considered:** Check `target/test-classes/` instead of `target/classes/` — rejected because `target/classes/` is always present after `maven_compile`, while `target/test-classes/` requires a full `mvn test` to exist. Checking `target/classes/` covers the common case and avoids confusing results when only `maven_compile` was run.

**Alternative considered:** Compare individual source-to-class mappings — rejected as too complex (would need to replicate compiler's source→class mapping, including inner classes). The "newest file" heuristic is simple and sufficient.

### 3. Contextual note for testOnly mode

**Decision:** Add a nullable `String note` field to the `BuildResult` record. When `testOnly=true`, **always** populate it with a contextual message. The message varies depending on what happened:

**No stale classes:**
`"Ran in testOnly mode (surefire:test). Lifecycle phases (generate-sources, compile) were skipped. If tests fail unexpectedly, re-run with testOnly=false for a full build."`

**Stale classes detected and auto-recompiled successfully:**
`"Ran in testOnly mode. Stale sources detected — auto-recompiled via compiler:compile compiler:testCompile (generate-sources was skipped). If tests still fail unexpectedly, re-run with testOnly=false for a full build."`

**Auto-recompile failed:**
Returns a compilation error result (no test execution). The note is not needed — error output speaks for itself.

The field is nullable and `@JsonInclude(NON_NULL)` ensures it's omitted from JSON when `testOnly=false` (full lifecycle — no caveat needed).

**Rationale:** The Maven build lifecycle is complex (`generate-sources` → `process-sources` → `compile` → `test-compile` → ...) and `surefire:test` bypasses all of it. The LLM caller needs to know this context to correctly interpret failures. The note also explains when auto-recompile happened, so the caller understands that `generate-sources` was still skipped and some failures may require a full rebuild.

**Alternative considered:** Return two `TextContent` items (note text + JSON) — rejected because it breaks the single-JSON-response pattern and makes parsing harder.

### 4. Check ordering and scope

**Decision:** The stale-classes check and auto-recompile run only when `testOnly=true`, after the existing `target/test-classes` guard passes, and before `runner.execute()` for `surefire:test`.

**Sequence:**
1. Extract `testOnly` parameter (default: `true`)
2. If `testOnly=true`: check `target/test-classes` exists → error if missing (existing guard, blocking)
3. If `testOnly=true` and guard passed: check stale classes
4. If stale detected: run `compiler:compile compiler:testCompile` via `runner.execute()`
   - If recompile fails → return compilation error result immediately (no test execution)
   - If recompile succeeds → set note with "auto-recompiled" context, continue
5. If not stale: set note with "compilation skipped" context
6. Execute `surefire:test`
7. Build `BuildResult` with `note` field (set when `testOnly=true`, null when `testOnly=false`)

**Rationale:** Guard ordering ensures we don't waste time on stale-class detection if compilation hasn't happened at all. Auto-recompile before test execution ensures the caller always gets results against fresh bytecode without needing a separate `maven_compile` call. The note is always present in testOnly mode so the caller understands the execution context.

### 5. Auto-recompile strategy: direct plugin goals

**Decision:** When stale classes are detected, run `compiler:compile compiler:testCompile` as a single Maven invocation (two goals in one `runner.execute()` call). These are direct plugin goals — they compile sources without triggering any lifecycle phases.

**What this skips vs full lifecycle:**
- `validate` — skipped (acceptable, only checks POM)
- `generate-sources` — skipped (this is the expensive phase: jOOQ, protobuf, JAXB)
- `process-sources` — skipped
- `generate-resources` / `process-resources` — skipped
- `generate-test-sources` / `process-test-sources` — skipped

**What this does:**
- `compiler:compile` — compiles `src/main/java/` → `target/classes/` (incremental)
- `compiler:testCompile` — compiles `src/test/java/` → `target/test-classes/` (incremental)

**Rationale:** Direct plugin goals give the minimal recompilation needed. Maven compiler plugin's incremental compilation means only changed files are recompiled (~1-3s). This is the sweet spot between "skip everything" (risk stale) and "full lifecycle" (slow).

**Alternative considered:** Run `mvn compile test-compile` (lifecycle phases) — rejected because these phases trigger everything up to and including compilation, including `generate-sources`. The whole point is to skip `generate-sources`.

**Alternative considered:** Run `compiler:compile` only (skip test compilation) — rejected because test sources may also have changed. Running both is still fast (incremental) and ensures consistency.

### 6. Utility method placement

**Decision:** Extract the stale-classes detection into a private static method `checkStaleClasses(Path projectDir)` returning `boolean` within `TestTool.java`. The auto-recompile call and note assembly stay inline in `create()`.

**Rationale:** Keeps the staleness logic self-contained and testable. The recompile + note composition is flow-dependent and reads better inline.

## Risks / Trade-offs

- **[Breaking change]** → Callers relying on implicit compilation via `testOnly=false` default will now get an error (from the existing guard) until they either pass `testOnly=false` explicitly or run `maven_compile` first. **Mitigation:** The guard error message is clear and actionable. The `INPUT_SCHEMA` description documents the new default.
- **[False positive recompiles]** → Editing a `.java` file without meaningful code changes (e.g., adding a comment) triggers auto-recompile. **Mitigation:** Acceptable — Maven compiler's incremental mode means the recompile is fast (~1-3s) and a no-op if bytecode is unchanged. Much better than running stale tests.
- **[False negatives]** → Modifying non-Java resources (properties files, XML configs) that affect test behavior won't trigger stale detection or recompile. **Mitigation:** Out of scope — the heuristic targets the most common case (Java source changes). Resource changes are rare and project-specific.
- **[Stale generated sources]** → If `generate-sources` output is stale (e.g., database schema changed but jOOQ classes not regenerated), auto-recompile won't fix it — it skips `generate-sources`. **Mitigation:** The note tells the caller to use `testOnly=false` for a full build if tests fail unexpectedly. This is the correct fallback for generated-source staleness.
- **[Recompile adds latency]** → Auto-recompile adds ~1-3s when stale classes detected. **Mitigation:** Only triggers when sources actually changed. Still much faster than a full lifecycle (~10-13s). When sources haven't changed (the common case), zero overhead.
- **[Performance on large `src/` trees]** → `Files.walk()` on thousands of files. **Mitigation:** Single-module scope (per SPEC.md) limits tree size. Expected ~1-5ms. If ever a concern, could be bounded with `Files.walk(path, maxDepth)`.
