Status: resolved
Blocked by: 06, 11

# 12: Add explicit online refresh and guarded legacy cleanup

## What to build

Complete the operator lifecycle with an intentional online refresh mode and a narrowly targeted cleanup operation that cannot remove rollback state without verified replacement state and explicit confirmation.

## Acceptance criteria

- [x] Migration performs GitHub acquisition only when the online-refresh option is explicitly supplied.
- [x] Online refresh uses the same production validation and composition rules as application acquisition.
- [x] Legacy cleanup refuses to run unless v2 verification succeeds in the same operation context.
- [x] Legacy cleanup also requires an explicit confirmation flag.
- [x] Cleanup resolves and validates the exact requested data directory and deletes only its legacy archive.
- [x] Refused and successful cleanup outcomes have equivalent human and JSON meaning and appropriate exit categories.
- [x] Automated verification does not claim that a real online refresh occurred.

## Comments

Resolved 2026-09-26. `migrate --online-refresh` now opens the same production
Ship Artwork lifetime used by the application, forces acquisition for unbundled
canonical Ship images, waits for each request to finish, and verifies successful
timestamps after v2 installation. Offline migration retains its acquisition
guard. Human and JSON reports show requested, succeeded, and failed counts;
failed sources are findings and preserve prior artwork.

`cleanup --confirm-legacy-cleanup` loads and verifies v2 during the cleanup
invocation, rejects missing or non-regular archive paths, and deletes only the
direct `icons.zip` in the resolved requested directory. Missing confirmation or
replacement state reports a refusal with exit 3; filesystem failures return 4.
Both output formats project the same cleanup status and target.

Scripted command-boundary tests covered successful and rejected HTTP content,
forced refresh of current v2 state, deterministic delayed completion, offline
behavior, cleanup refusal, and exact-target deletion. A failed forced retry
after an earlier success now reports failure while retaining the old pixels.
Focused artwork suites passed. `.\gradlew.bat clean build` passed, including the
complete JUnit and artifact verification tasks. Standards and spec re-review
found no remaining issues.

Separate manual operator checks used isolated build fixtures: offline inspect
and migrate returned 3 for the fixture's unmatched legacy entry, verify returned
0, cleanup without confirmation returned 3, and confirmed cleanup returned 0.
A live `--online-refresh` against a fixture augmented with an upstream
`APU_Cruiser.png` source fetched and persisted one image; four other fixture
sources failed, so migrate reported 1 success, 4 failures, and exit 3. The
resulting v2 archive verified with exit 0. The automated tests used scripted
HTTP and are not evidence of that live result.
