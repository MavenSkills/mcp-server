## Why

`StackTraceProcessor` does not recognize `Suppressed:` lines from Java stack traces (`Throwable.addSuppressed()`). Suppressed exceptions end up in `currentFrames` as regular frame lines, causing two bugs: (1) `Suppressed:` header lines are collapsed together with framework frames when filtering is enabled (`appPackage`), and (2) `Caused by:` lines inside a `Suppressed:` block are incorrectly recognized as top-level segment boundaries, breaking the entire parse tree.

## What Changes

- Parse `Suppressed:` lines as sub-segment boundaries within their parent segment, preserving the tree structure of Java exception chains
- Correctly scope `Caused by:` lines that appear inside a `Suppressed:` block (indented) so they are not treated as top-level segment boundaries
- Apply the same intelligent frame collapsing (app vs framework) to suppressed exception frames
- Preserve suppressed exception headers during hard-cap truncation (they carry diagnostic value similar to `Caused by:` headers)

## Capabilities

### New Capabilities

- `suppressed-exception-parsing`: Handling of `Suppressed:` exception blocks in stack trace processing, including nested `Caused by:` chains within suppressed blocks, indentation-aware segment parsing, and frame collapsing rules for suppressed sub-trees.

### Modified Capabilities

_(none — existing specs don't cover StackTraceProcessor parsing rules; the new capability fully encapsulates this feature)_

## Impact

- **Code**: `StackTraceProcessor` (parser, segment model, collapsing logic, hard-cap logic)
- **Tests**: `StackTraceProcessorTest` — new scenarios for suppressed exceptions
- **API**: No changes to `maven_test` tool schema or `BuildResult`/`TestFailure` records — this is purely an internal improvement to stack trace output quality
