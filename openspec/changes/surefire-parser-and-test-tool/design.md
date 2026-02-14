## Context

Server has 2 working tools (compile, clean) with compilation output parsing. This change adds the most complex tool: `maven_test`. The key difference from compile is that test results come from Surefire XML reports on the filesystem, not from stdout parsing. The tool also has branching logic: if XML reports exist → parse test results; if not → fall back to compilation error parsing.

Existing code: `CompilationOutputParser`, `BuildResult`, `CompilationError`, `MavenRunner`, `ToolUtils`, tool pattern from `CompileTool`/`CleanTool`.

## Goals / Non-Goals

**Goals:**
- `SurefireReportParser` — JAXP DOM parser for `target/surefire-reports/TEST-*.xml`
- `TestSummary` and `TestFailure` models
- Stack trace truncation (default 50 lines, configurable)
- `maven_test` tool with `testFilter`, `stackTraceLines`, `args` parameters
- Compilation failure fallback when no Surefire XML
- Update `BuildResult` to use typed `TestSummary`/`List<TestFailure>` instead of `Object`/`List<?>`

**Non-Goals:**
- Failsafe (integration test) report parsing
- Test re-run or flaky test detection
- Code coverage integration

## Decisions

### D1: JAXP DOM parser (built into JDK)

**Decision:** Use `javax.xml.parsers.DocumentBuilder` for XML parsing. No external XML library.

**Alternatives:** SAX/StAX (streaming) — overkill for small files; JDOM/dom4j — unnecessary dependency.

**Rationale:** Surefire XML files are small (KB). DOM parsing is simple and sufficient. Zero additional dependencies.

### D2: Null return for missing reports

**Decision:** `SurefireReportParser.parse()` returns `null` when no reports are found. The tool handler checks for null and falls back to compilation error parsing.

**Rationale:** Clean separation of concerns. Parser doesn't know about compilation errors. Tool handler implements the fallback logic.

### D3: Update BuildResult to use typed fields

**Decision:** Change `BuildResult.summary` from `Object` to `TestSummary` and `BuildResult.failures` from `List<?>` to `List<TestFailure>`. This is a non-breaking change since the JSON shape is identical.

**Rationale:** Type safety. The `Object`/`List<?>` types were temporary placeholders from Milestone 3.

### D4: testFilter mapping

**Decision:** If `testFilter` is non-null and non-empty, add `-Dtest=<value>` to the Maven args. Also add `-DfailIfNoTests=false` to avoid failure when filter matches no tests.

**Rationale:** Direct passthrough to Maven's Surefire `-Dtest` parameter. Adding `-DfailIfNoTests=false` prevents confusing errors when the filter is wrong.

## Risks / Trade-offs

**[Risk] Surefire XML format varies** → The format is standardized by Maven Surefire Plugin and stable across versions. Test with real XML fixtures.

**[Trade-off] Stack trace truncation loses context** → 50 lines is a pragmatic default. The `stackTraceLines` parameter lets the agent request more if needed.
