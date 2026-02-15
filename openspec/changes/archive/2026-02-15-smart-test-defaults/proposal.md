## Why

The `testOnly` parameter currently defaults to `false`, forcing a full Maven lifecycle (`generate-sources` → `compile` → `test`) on every `maven_test` call. In practice, code is already compiled in the vast majority of test invocations (the agent just ran `maven_compile`, or is re-running a failing test). Benchmarks show `testOnly=true` saves 10-13 seconds per invocation — a 36% speedup. Additionally, when `testOnly=true` and sources have changed since last compilation, tests run against stale bytecode silently. A stale-classes warning would prevent confusing false-positive test results.

## What Changes

- **BREAKING**: Change `testOnly` default from `false` to `true` — `maven_test` will run `surefire:test` by default, skipping lifecycle phases. Callers that relied on implicit compilation must now run `maven_compile` first or pass `testOnly: false`.
- Add stale-classes detection: before running `surefire:test`, compare the newest source file timestamp under `src/` against the newest class file timestamp under `target/classes/`. If sources are newer, include a warning in the response: *"Warning: sources modified since last compilation. Test results may be stale. Run maven_compile to recompile."* This is a non-blocking warning — tests still execute.

## Capabilities

### New Capabilities

- `stale-classes-detection`: Timestamp-based heuristic comparing source files (`src/`) against compiled classes (`target/classes/`) to detect when recompilation may be needed.

### Modified Capabilities

- `test-tool`: Default value of `testOnly` changes from `false` to `true`. New stale-classes warning integrated into the pre-flight check sequence.

## Impact

- **Code:** `TestTool.java` — change default in `INPUT_SCHEMA` description and `extractBoolean` call; add stale-classes check logic before `runner.execute()`
- **API:** Breaking change for callers that relied on `maven_test` implicitly compiling. The pre-flight guard (already implemented) ensures a clear error when classes are missing, so failures are actionable.
- **Dependencies:** None — uses `java.nio.file` APIs for timestamp comparison
- **Risk:** Low — the existing pre-flight guard catches "never compiled" case; the new stale-classes check catches "compiled but outdated" case. Together they cover both failure modes for `testOnly=true`.
