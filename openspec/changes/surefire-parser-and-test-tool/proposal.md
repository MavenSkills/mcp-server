## Why

The server can compile and clean but cannot run tests — the most frequent agent operation in the edit→compile→test→fix cycle. This change adds `maven_test` (the most complex tool) with Surefire XML report parsing for reliable, structured test results. After this, the agent can run specific tests, get pass/fail details with failure messages and stack traces, and detect compilation errors during the test phase.

## What Changes

- New `SurefireReportParser` that reads `target/surefire-reports/TEST-*.xml` files using JAXP DOM
- New `TestSummary` model (testsRun, testsFailed, testsSkipped, testsErrored)
- New `TestFailure` model (testClass, testMethod, message, stackTrace)
- Stack trace truncation to 50 lines by default, configurable via `stackTraceLines` parameter
- New `maven_test` MCP tool with `testFilter` parameter mapping to `-Dtest=...`
- Fallback: if Surefire XML not found after test failure, fall back to compilation error parsing (tests didn't compile)
- Tool registered on McpSyncServer alongside existing compile and clean tools

## Capabilities

### New Capabilities
- `surefire-parsing`: Parser for Maven Surefire XML test reports — extracts test counts, failure details, and truncated stack traces
- `test-tool`: MCP tool `maven_test` — runs tests with optional filtering and returns structured test results

### Modified Capabilities
_(none)_

## Impact

- **New files:** ~6 Java source files (parser, models, tool handler) + sample XML fixtures
- **Modified files:** `MavenMcpServer.java` (register test tool), `BuildResult.java` (use typed TestSummary/TestFailure instead of Object/List<?>)
- **MCP API:** Server now reports 3 tools in `tools/list`
- **Dependencies:** None new (JAXP is built into JDK)
