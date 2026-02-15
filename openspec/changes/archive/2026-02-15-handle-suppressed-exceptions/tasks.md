## 1. Structural line detection

- [x] 1.1 Add `isStructuralLine(String line)` method to `StackTraceProcessor` — returns `true` when the stripped line starts with `Suppressed:` or when the stripped line starts with `Caused by:` AND the original line has leading whitespace
- [x] 1.2 Unit tests for `isStructuralLine`: `Suppressed:` with tab prefix, indented `Caused by:` with tab prefix, top-level `Caused by:` (no whitespace → false), regular `at` frame (false), `... 42 more` line (false)

## 2. Frame collapsing integration

- [x] 2.1 Update `addCollapsedFrames` to check `isStructuralLine` before `isApplicationFrame` — if structural, flush pending framework count and emit the line unconditionally
- [x] 2.2 Apply the same structural line check in `addRootCauseFrames`

## 3. Tests

- [x] 3.1 Test: suppressed header preserved between framework frames (framework count split into two markers around the header)
- [x] 3.2 Test: indented `Caused by:` header inside suppressed block preserved in output
- [x] 3.3 Test: app frames inside suppressed block preserved, framework frames collapsed
- [x] 3.4 Test: end-to-end — full stack trace with main exception, suppressed + nested caused-by, and top-level caused-by root cause; verify all 4+ headers present and framework frames collapsed
- [x] 3.5 Test: no filtering (appPackage null) — entire suppressed block passes through unchanged
