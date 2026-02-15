## 1. Model Change

- [x] 1.1 Add nullable `String note` field to `BuildResult` record (existing `@JsonInclude(NON_NULL)` handles omission when null)
- [x] 1.2 Update all existing `BuildResult` constructor calls to pass `null` for `note` (maintain backward compat)

## 2. Stale-Classes Detection

- [x] 2.1 Add private static method `checkStaleClasses(Path projectDir)` in `TestTool.java` returning `boolean` — uses `Files.walk()` to compare newest `.java` under `src/` vs newest `.class` under `target/classes/`; returns `false` if either directory is missing or empty

## 3. Auto-Recompile and Note Integration

- [x] 3.1 In `TestTool.create()`, after the existing `target/test-classes` guard: call `checkStaleClasses()`, if stale then execute `compiler:compile compiler:testCompile` via `runner.execute()` — if recompile fails, return compilation error result immediately
- [x] 3.2 Build `note` string: base testOnly context message when not stale; auto-recompiled context message when stale and recompile succeeded
- [x] 3.3 Pass `note` to `BuildResult` constructor in all test result branches (success, failure, no-XML)

## 4. Default Change

- [x] 4.1 Change `ToolUtils.extractBoolean(params, "testOnly", false)` to `ToolUtils.extractBoolean(params, "testOnly", true)` in `TestTool.create()`
- [x] 4.2 Update `testOnly` description in `INPUT_SCHEMA` to reflect new default (`true`) and include proactive guidance for LLM callers: when to use `false` (build config changes, generated source templates, new dependencies, resource files)

## 5. Tests

- [x] 5.1 Unit test: `checkStaleClasses` returns `true` when source is newer than class
- [x] 5.2 Unit test: `checkStaleClasses` returns `false` when class is newer than source
- [x] 5.3 Unit test: `checkStaleClasses` returns `false` when `src/` or `target/classes/` missing
- [x] 5.4 Unit test: default (omitted `testOnly`) uses `surefire:test` goal
- [x] 5.5 Unit test: `testOnly=false` uses `test` goal and `note` is null in response
- [x] 5.6 Unit test: `testOnly=true`, no stale classes → `note` contains testOnly context message
- [x] 5.7 Unit test: `testOnly=true`, stale classes detected → auto-recompile invoked, then `surefire:test` runs, `note` contains auto-recompiled message
- [x] 5.8 Unit test: `testOnly=true`, stale classes detected, recompile fails → returns compilation error, no `surefire:test` invocation
