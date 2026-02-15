## Requirements

### Requirement: Stale classes detection via timestamp heuristic
The system SHALL detect stale compiled classes by comparing file timestamps: the newest `.java` file under `src/` against the newest `.class` file under `target/classes/`. If the newest source file is newer than the newest class file, the classes SHALL be considered stale.

The detection SHALL use `Files.walk()` with `Files.getLastModifiedTime()` to find the maximum timestamp in each tree.

If either `src/` or `target/classes/` does not exist or contains no matching files, the detection SHALL return "not stale" (no false positives on missing directories).

#### Scenario: Sources newer than classes — stale detected
- **WHEN** the newest `.java` file under `src/` has a modification time newer than the newest `.class` file under `target/classes/`
- **THEN** the detection SHALL report stale classes

#### Scenario: Classes newer than sources — not stale
- **WHEN** the newest `.class` file under `target/classes/` has a modification time newer than or equal to the newest `.java` file under `src/`
- **THEN** the detection SHALL report no stale classes

#### Scenario: No source files
- **WHEN** `src/` directory does not exist or contains no `.java` files
- **THEN** the detection SHALL report no stale classes

#### Scenario: No class files
- **WHEN** `target/classes/` directory does not exist or contains no `.class` files
- **THEN** the detection SHALL report no stale classes
