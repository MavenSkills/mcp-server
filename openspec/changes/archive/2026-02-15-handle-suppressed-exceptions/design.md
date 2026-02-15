## Context

`StackTraceProcessor` parses raw stack traces into a flat list of `Segment` records (header + frames), then applies intelligent frame collapsing (app vs framework) and hard-cap truncation. The raw stack traces arrive from Surefire XML via `failure.getTextContent()`, which preserves original JDK `Throwable.printStackTrace()` formatting — including tab indentation.

JDK format for suppressed exceptions:
```
java.lang.Exception: main
	at com.example.Main.main(Main.java:5)
	Suppressed: java.lang.Exception: sup1
		at com.example.Main.close(Main.java:10)
		Caused by: java.lang.RuntimeException: inner
			at com.example.Main.inner(Main.java:15)
	Suppressed: java.lang.Exception: sup2
		at com.example.Main.close2(Main.java:20)
Caused by: java.lang.Exception: root
	at com.example.Main.work(Main.java:25)
```

Key formatting rules from JDK `Throwable.printEnclosedStackTrace()`:
- `Suppressed:` lines are prefixed with parent's prefix + `\t` (one extra tab)
- `Caused by:` within a suppressed block inherits the suppressed prefix (indented)
- Top-level `Caused by:` has no leading whitespace

Current `parseSegments` uses `line.startsWith("Caused by:")` — this already correctly skips indented `\tCaused by:` within suppressed blocks. **The segment parsing is not broken for standard JDK format.**

The actual bug is in frame classification: `isApplicationFrame` returns `false` for `Suppressed:` and indented `Caused by:` header lines (they don't start with `at `), so `addCollapsedFrames` collapses them into "... N framework frames omitted" — losing diagnostic information.

## Goals / Non-Goals

**Goals:**
- Preserve `Suppressed:` exception headers and their nested `Caused by:` headers in collapsed output
- Apply frame collapsing (app vs framework) to frames within suppressed blocks
- Keep the existing `Segment` model flat (no recursive tree)
- Handle standard JDK `printStackTrace()` format

**Non-Goals:**
- Recursive segment tree model (over-engineering for this use case)
- Handling non-standard stack trace formats that strip indentation
- Suppressed-aware hard-cap logic (suppressed blocks are secondary diagnostic info — ok to truncate)

## Decisions

### Decision 1: Structural line detection instead of model change

Introduce a `isStructuralLine(String line)` classifier that recognizes lines whose stripped content starts with `Suppressed:` or `Caused by:` (but the original line has leading whitespace, meaning it's inside a suppressed block). These lines are exception headers that must always be preserved — they carry diagnostic value and are not stack frames.

**Alternative considered:** Recursive `Segment` tree with `List<Segment> suppressed` children. Rejected because:
- Every consumer of `Segment` (collapsing, hard-cap, rendering) would need tree traversal
- The flat model works — suppressed content lives as frame lines within the parent segment
- The fix is a 5-line change in frame collapsing, not an architecture rewrite

### Decision 2: Preserve structural lines in addCollapsedFrames

In `addCollapsedFrames` (and `addRootCauseFrames`), before checking `isApplicationFrame`, check `isStructuralLine`. If true, flush any pending framework count and emit the line unconditionally. This ensures `Suppressed:` headers and nested `Caused by:` headers are never collapsed.

Frame lines (`\t\tat ...`) within suppressed blocks go through the normal app-vs-framework classification. The existing `isApplicationFrame` already strips leading whitespace before checking `at `, so `\t\tat com.example.Foo.bar(...)` will correctly match the app package — no changes needed there.

### Decision 3: No changes to parseSegments

`parseSegments` uses `line.startsWith("Caused by:")` which only matches non-indented lines. In JDK format, `Caused by:` inside a suppressed block is always indented (`\tCaused by:`), so it already correctly stays as a frame line within the parent segment. No change needed.

### Decision 4: No changes to hard-cap logic

`applyHardCap` preserves the root cause (last segment) header. Suppressed exceptions within earlier segments are secondary diagnostic info. If the hard cap truncates them, that's acceptable — the root cause chain is more important.

## Risks / Trade-offs

- **[Non-standard formats]** → If some tool strips indentation before passing stack traces to us, `Caused by:` inside suppressed blocks would match `startsWith("Caused by:")` and break segment parsing. Mitigation: this is a pre-existing issue not introduced by this change; Surefire XML preserves JDK format.
- **[Framework count accuracy]** → Structural lines interrupt framework frame counting, so the "... N framework frames omitted" marker correctly reflects only consecutive framework frames between structural lines. This is the desired behavior.
