## ADDED Requirements

### Requirement: Preserve suppressed exception headers in collapsed output

When frame collapsing is active (`appPackage` is non-blank), the `StackTraceProcessor` SHALL treat lines whose stripped content starts with `Suppressed:` as structural header lines. Structural header lines SHALL always be emitted in the output — they SHALL NOT be counted as framework frames or collapsed into "... N framework frames omitted" markers.

#### Scenario: Suppressed header preserved when filtering is active

- **WHEN** `StackTraceProcessor.process` is called with a non-blank `appPackage` and the stack trace contains a `Suppressed:` line (e.g. `\tSuppressed: java.lang.Exception: resource cleanup failed`)
- **THEN** the output SHALL contain the `Suppressed:` header line verbatim

#### Scenario: Suppressed header preserved between framework frames

- **WHEN** a `Suppressed:` header line appears between consecutive framework (non-app) `at` frames
- **THEN** the framework frames before the header SHALL be collapsed into one "... N framework frames omitted" marker, the `Suppressed:` header SHALL appear, and framework frames after the header SHALL be collapsed into a separate marker

#### Scenario: No filtering — suppressed header passes through unchanged

- **WHEN** `appPackage` is null or blank
- **THEN** all lines including `Suppressed:` headers SHALL pass through unchanged (no collapsing)

### Requirement: Preserve indented Caused by headers in collapsed output

When frame collapsing is active, the `StackTraceProcessor` SHALL treat lines whose stripped content starts with `Caused by:` AND whose original line has leading whitespace as structural header lines. These lines represent `Caused by:` chains nested within `Suppressed:` blocks and SHALL always be preserved in the output.

#### Scenario: Indented Caused by header inside suppressed block preserved

- **WHEN** the stack trace contains an indented `Caused by:` line (e.g. `\tCaused by: java.lang.RuntimeException: inner`) within a suppressed block
- **THEN** the output SHALL contain that `Caused by:` header line verbatim

#### Scenario: Top-level Caused by is not affected

- **WHEN** a `Caused by:` line has no leading whitespace (top-level segment boundary)
- **THEN** it SHALL continue to be handled as a segment header by `parseSegments` (existing behavior, unchanged)

### Requirement: Apply frame collapsing within suppressed blocks

Stack frame lines (`at ...`) within suppressed exception blocks SHALL be subject to the same app-vs-framework classification and collapsing as frames in top-level segments. The `isApplicationFrame` method already strips leading whitespace before checking the `at ` prefix, so indented frames (e.g. `\t\tat com.example.Foo.bar(...)`) SHALL be correctly classified.

#### Scenario: App frame inside suppressed block is preserved

- **WHEN** a suppressed block contains a frame `\t\tat com.example.app.Service.close(Service.java:42)` and `appPackage` is `com.example.app`
- **THEN** that frame SHALL appear in the output

#### Scenario: Framework frame inside suppressed block is collapsed

- **WHEN** a suppressed block contains consecutive non-app frames (e.g. `\t\tat org.springframework.tx.TransactionManager.close(...)`)
- **THEN** those frames SHALL be collapsed into a "... N framework frames omitted" marker

### Requirement: Segment parsing unchanged for standard JDK format

The `parseSegments` method SHALL continue to use `line.startsWith("Caused by:")` (no leading whitespace) as the segment boundary condition. In standard JDK `Throwable.printStackTrace()` format, `Caused by:` lines within suppressed blocks are indented with at least one tab character and SHALL NOT match this condition — they remain as frame lines within the parent segment.

#### Scenario: Indented Caused by does not create a new segment

- **WHEN** `parseSegments` encounters a line `\tCaused by: java.lang.RuntimeException: inner` (leading tab)
- **THEN** it SHALL NOT create a new segment — the line SHALL be added to the current segment's frames list

#### Scenario: Non-indented Caused by creates a new segment

- **WHEN** `parseSegments` encounters a line `Caused by: java.lang.Exception: root` (no leading whitespace)
- **THEN** it SHALL create a new segment with that line as the header (existing behavior, unchanged)

### Requirement: Full suppressed exception chain in end-to-end output

When processing a stack trace containing a main exception, suppressed exceptions with nested `Caused by:` chains, and a top-level `Caused by:` root cause, the processor SHALL produce output that preserves the complete diagnostic structure: all exception headers (main, suppressed, nested caused-by, root cause) SHALL be present, with only framework `at` frames collapsed.

#### Scenario: Complete stack trace with suppressed and caused-by chains

- **WHEN** `StackTraceProcessor.process` receives a stack trace in standard JDK format:
  ```
  java.lang.Exception: main
  	at com.example.app.Main.run(Main.java:10)
  	at org.framework.Runner.execute(Runner.java:50)
  	Suppressed: java.lang.Exception: cleanup failed
  		at com.example.app.Resource.close(Resource.java:20)
  		at org.framework.Pool.release(Pool.java:80)
  		Caused by: java.lang.RuntimeException: IO error
  			at com.example.app.Resource.flush(Resource.java:30)
  			at org.framework.IO.sync(IO.java:100)
  Caused by: java.lang.IllegalStateException: root cause
  	at com.example.app.Main.init(Main.java:5)
  	at org.framework.Bootstrap.start(Bootstrap.java:200)
  ```
  with `appPackage = "com.example.app"` and `stackTraceLines = 0`
- **THEN** the output SHALL contain all 5 exception headers:
  - `java.lang.Exception: main`
  - `Suppressed: java.lang.Exception: cleanup failed` (with original indentation)
  - `Caused by: java.lang.RuntimeException: IO error` (with original indentation)
  - `Caused by: java.lang.IllegalStateException: root cause`
- **AND** application frames (`com.example.app.*`) SHALL be preserved
- **AND** framework frames (`org.framework.*`) SHALL be collapsed into "... N framework frames omitted" markers
