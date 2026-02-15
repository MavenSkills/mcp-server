## 1. Core Implementation

- [x] 1.1 Create `TestFailureDeduplicator` class in `io.github.mavenmcp.parser` with private `GroupKey` record and static `deduplicate(List<TestFailure>)` method. Implement `LinkedHashMap`-based grouping by `(message, stackTrace)`, summary formatting with N=3 threshold, `testOutput` merging with `"\n---\n"` separator, and null handling per spec.

## 2. Integration

- [x] 2.1 In `TestTool.create()`, call `TestFailureDeduplicator.deduplicate()` on the result of `processStackTraces()` and pass the deduplicated list to `BuildResult` constructor.

## 3. Tests

- [x] 3.1 Unit test `TestFailureDeduplicatorTest`: single failure passthrough (unchanged), multiple identical failures grouped into one, mixed groups (different messages produce separate entries), `testMethod` summary format for <=3 and >3 methods, `testClass` consolidation (same class vs. multiple classes), `testOutput` merging (mixed nulls, all nulls), insertion-order preservation, null `message`/`stackTrace` grouping.
