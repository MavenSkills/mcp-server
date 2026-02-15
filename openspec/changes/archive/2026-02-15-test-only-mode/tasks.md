## 1. Schema & Parameter Extraction

- [x] 1.1 Add `testOnly` boolean property to `INPUT_SCHEMA` in `TestTool.java` (type: boolean, description mentioning `surefire:test` and default `false`)
- [x] 1.2 Extract `testOnly` parameter in `create()` using `ToolUtils.extractBoolean(params, "testOnly", false)`

## 2. Core Logic

- [x] 2.1 Add pre-flight guard: when `testOnly=true`, check `config.projectDir().resolve("target/test-classes")` exists via `Files.isDirectory()`; if missing, return error `CallToolResult` with message "Project not compiled. Run maven_compile first or set testOnly=false."
- [x] 2.2 Switch goal passed to `runner.execute()` from hardcoded `"test"` to `testOnly ? "surefire:test" : "test"`

## 3. Tests

- [x] 3.1 Add unit test: `testOnly=false` (or omitted) executes goal `"test"` — verify current behavior unchanged
- [x] 3.2 Add unit test: `testOnly=true` with `target/test-classes` present executes goal `"surefire:test"`
- [x] 3.3 Add unit test: `testOnly=true` without `target/test-classes` returns error without invoking Maven
- [x] 3.4 Add unit test: `testOnly=true` combined with `testFilter` passes both `-Dtest=...` args and uses `surefire:test` goal
