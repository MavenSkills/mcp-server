## Why

Projects with heavy `generate-sources` plugins (jOOQ + Testcontainers, protobuf, JAXB, etc.) suffer slow `mvn test` runs even when code is already compiled — Maven re-executes the entire lifecycle from `generate-sources` through `compile` to `test`. A `testOnly` mode that runs `surefire:test` directly skips all prior phases, giving near-instant test feedback when the build is already up to date.

## What Changes

- Add a `testOnly` boolean parameter (default: `false`) to the `maven_test` tool
- When `testOnly=false` (default): current behavior — runs `mvn test` (full lifecycle)
- When `testOnly=true`: runs `mvn surefire:test` — executes only the Surefire plugin, skipping all earlier phases
- Pre-flight guard: before running `surefire:test`, check that `target/test-classes` exists; if missing, return an error: *"Project not compiled. Run maven_compile first or set testOnly=false."*

## Capabilities

### New Capabilities

_(none — this extends an existing capability)_

### Modified Capabilities

- `compile-tool`: No changes needed — already produces `target/test-classes`
- `test-tool`: _(no existing spec by this exact name — see below)_

> **Note:** There is no existing `test-tool` spec in `openspec/specs/`. The changes are scoped to `TestTool.java` only: new parameter in `INPUT_SCHEMA`, goal selection logic in `create()`, and `target/test-classes` pre-check. This does not require a new capability spec — it's an additive parameter on the existing tool.

## Impact

- **Code:** `TestTool.java` — `INPUT_SCHEMA` (new `testOnly` property), `create()` method (goal selection + guard), `buildArgs()` (no change needed — args are goal-independent)
- **API:** Additive, non-breaking — new optional boolean parameter with `false` default preserves existing behavior
- **Dependencies:** None — `surefire:test` is a standard Maven goal available in any project using Surefire
- **Risk:** Low — the guard check prevents confusing failures when classes aren't compiled
