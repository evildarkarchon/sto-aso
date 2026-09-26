Status: resolved
Blocked by: 09, 10

# 11: Provide offline inspect, migrate, and verify operations

## What to build

Give maintainers a headless operator tool that uses the same archive, identity, migration, and validation implementation as the application while remaining explicit about its target and offline behavior.

## Acceptance criteria

- [x] A Gradle Java execution task exposes inspect, migrate, and verify operations.
- [x] Every invocation requires an explicit data-directory option and never applies application directory inference.
- [x] Inspect inventories legacy and v2 state without writing or invoking remote acquisition.
- [x] Migrate is offline by default and fails observably if implementation code attempts network access.
- [x] Verify checks every v2 entry, digest, identity, schema, recipe, and metadata relationship without writing.
- [x] Human-readable and JSON output are projections of the same structured outcome.
- [x] Invalid arguments, verification findings, and operational failures use distinct nonzero exit-status categories; unmatched legacy entries are findings.

## Comments

Resolved 2026-09-25. Added a Java 25 headless `shipArtworkTool` Gradle task and
an assembled direct launcher for scripts that need the tool's exact exit codes.
All operations require `--data-directory`. Inspect and verify read the shared
archive and legacy migration implementations without constructing a mutable
artwork lifetime. Migrate uses the application's lazy migration with an adapter
that fails loudly if source acquisition is attempted, preserves `icons.zip`, and
reports v2 installation failures.

Verification now rejects orphan or unknown manifest metadata in addition to the
existing schema, recipe, identity, digest, PNG, and archive-structure checks.
Invalid archive contents are findings; filesystem failures are operational errors.
Human and JSON reports render the same outcome, including per-entry legacy
decisions. Inspect labels candidates as eligible without claiming they were
migrated. The direct launcher was exercised with exit codes 2, 3, and 4.

Focused artwork tests and an offline inspect, migrate, verify pass against the
frozen legacy fixture succeeded. `.\gradlew.bat clean build` passed on Java 25
with 834 tests, no failures or skips. Independent standards and spec review
found no remaining issues after documentation and exit-category fixes.
