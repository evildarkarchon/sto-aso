# GameData Refresh seam — design decisions

Outcome of the architecture review and grilling session for deepening the GameData Refresh transaction.
This is the input for a later implementation change; no production behavior is implemented by this document.

Vocabulary: architecture terms per `/codebase-design` (module, interface, implementation, seam, adapter,
depth, locality, leverage); domain terms per `CONTEXT.md`.

## Problem being solved

- `ui.workers.UpdateDataFiles` is a 510-line `SwingWorker` that owns freshness, remote manifest parsing,
  download, digest verification, staged installation, rollback, cleanup and event-thread reporting.
- Its effective test interface is its protected implementation. `UpdateDataFilesTest` subclasses it to replace
  remote reads, downloads, individual file moves, manifest publication and reader behavior.
- Changing installation mechanics therefore changes test fixtures even when observable GameData Refresh behavior
  is unchanged. The interface is not the test surface.
- When `hashes.md5` is missing, the current implementation creates a fresh local manifest before contacting the
  remote source. A subsequent remote failure can suppress another refresh attempt for seven days.

## Domain contract

- **GameData Refresh** is the canonical term for a background attempt to replace shipped GameData as one coherent
  set.
- A successful refresh becomes active on the next application launch. Live reload remains out of scope.
- A failure reported while the application remains running does not make a partial replacement current.
- Process or machine termination, and the durability required to recover from it, are outside this guarantee.

## Change envelope

- The refactor preserves the existing download-only and restart-to-apply behavior.
- The fixed GameData filename set, HTTPS source, MD5 manifest compatibility, seven-day freshness interval,
  single remote attempt and atomic-move fallback remain unchanged.
- The sole intentional behavior correction is that a missing manifest followed by refresh failure remains due for
  retry. No live manifest is created or refreshed until the remote manifest has been validated.
- Retries, explicit network timeouts, authenticated manifests, stronger digests, cross-process locking and crash
  recovery are separate hardening opportunities.

## Deep module

- A final synchronous `io.GameDataRefresh` module owns freshness and the complete refresh transaction for one data
  directory.
- Its external interface lets a caller consult refresh eligibility and perform one refresh. It returns immutable
  outcomes and does not depend on Swing.
- The module owns the fixed filename set, remote-manifest validation, path validation, stream lifetime, hashing,
  staging, backup, installation, manifest publication, rollback and cleanup.
- Depth comes from hiding those rules behind the small interface. Bootstrap, the Swing adapter and tests all gain
  leverage from the same behavior, while changes to recovery mechanics retain locality inside the implementation.

## Internal seams and adapters

- `GameDataRefreshSource` is a package-private internal seam. It supplies the digest manifest and one
  module-selected GameData file without choosing any local path.
- The production adapter reads the existing GitHub HTTPS locations. A deterministic scripted adapter supplies
  content and failures in tests. Two adapters make this a real seam.
- Ordinary filesystem behavior is tested with real temporary directories rather than a general filesystem
  abstraction.
- One package-private replacement seam covers live-file and manifest moves. The production adapter uses
  `Files.move`; a fault-injection adapter lets tests reproduce mid-install failure and unsupported atomic
  replacement. No other filesystem operations are exposed.
- Only `GameDataRefresh` and its immutable outcome are public. Internal source and replacement adapters remain
  package-private so callers do not need to understand them.

## Outcome model

The refresh returns one immutable outcome with exactly one of these statuses:

- `CURRENT`: no changed files and no failure.
- `REFRESHED`: a non-empty immutable changed-file set and no failure.
- `FAILED`: no claimed changes, plus a stable failure category, a diagnostic cause when available and a retained
  recovery path when recovery artifacts remain.

Failure categories stay coarser than implementation steps:

- remote acquisition;
- verification;
- installation;
- recovery.

Non-fatal cleanup diagnostics may accompany `CURRENT` or `REFRESHED`. Failure to remove a private staging path
must not misreport a successfully committed GameData set as failed. Unexpected programming errors are not converted
into operational outcomes.

## Freshness

- Freshness remains based on the modification time of `hashes.md5`; no clock seam is introduced.
- A missing manifest or a manifest at least seven days old is due.
- `CURRENT` and `REFRESHED` update the live manifest timestamp.
- `FAILED` leaves the refresh due for a later attempt.
- Tests control manifest modification times directly.

## Transaction protocol

One refresh attempt performs these steps in order:

1. Read and validate the complete remote manifest.
2. Compare the remote hashes with the live manifest or hashes calculated from existing GameData files.
3. Download every changed file into a private staging directory.
4. Verify every staged file before changing any live file.
5. Back up every affected live file and the live manifest when present.
6. Replace the changed GameData files.
7. Publish the validated remote manifest last as the commit point.
8. Roll back caught installation failures.
9. Remove staging artifacts after success or successful rollback.

Pre-installation failure removes the private staging directory. Incomplete rollback returns `FAILED`, does not
publish the new manifest and retains the recovery directory so the Swing adapter can report its exact location.

## Concurrency and interruption

- One application-owned `GameDataRefresh` instance serializes attempts for its data directory.
- A concurrent caller joins the active attempt and receives that attempt's immutable outcome rather than starting
  a second transaction.
- Cross-process coordination is out of scope.
- No public cancellation seam is added because no production caller currently cancels the worker.
- An already-interrupted thread may fail at a safe phase before the first live replacement.
- Once installation starts, the module runs through commit or rollback; interruption cannot strand a partial set.

## Application wiring

- After resolving the data directory, `AppBootstrap` creates one application-owned `GameDataRefresh` instance.
- Bootstrap consults that instance's freshness policy and passes the same instance through its background-job seam.
- `SwingWorkerExecutor` remains the production background-job adapter.
- `ui.workers.UpdateDataFiles` contracts to a final Swing adapter that runs the synchronous module and reports its
  outcome on the event-dispatch thread.
- The Swing adapter logs `CURRENT`, `REFRESHED` and `FAILED`, including restart guidance, diagnostic causes,
  non-fatal cleanup warnings and retained recovery locations. It does not open a modal dialog.
- `FileDownloader` and the unused static `SwingWorkerExecutor.downloadFile` are deleted because remote streaming is
  owned by the source adapter.
- Freshness and outcome semantics move out of the Swing package.

## Compatibility

- No deprecated compatibility interface is retained for the protected methods or nested result enum on
  `UpdateDataFiles`.
- They have no production callers outside the repository, and keeping them would preserve the implementation-shaped
  test surface this change removes.
- All repository callers and tests migrate in the same change.

## Tests

The deep module interface becomes the primary test surface. Existing cases move from the Swing-worker subclass
hierarchy and continue to cover:

1. unexpected remote filename rejection;
2. absolute remote filename rejection;
3. incomplete manifest rejection;
4. unchanged GameData returning `CURRENT`;
5. partial or failed remote reads;
6. UTF-8 manifest decoding;
7. digest mismatch without live changes;
8. mid-install failure restoring earlier replacements;
9. manifest-publication failure restoring installed files;
10. atomic replacement fallback.

New regression and contract cases cover:

11. missing manifest plus remote failure remains due;
12. concurrent callers share one attempt and outcome;
13. interruption before installation is safe;
14. interruption during installation completes or rolls back;
15. cleanup warning preserves `CURRENT` or `REFRESHED`;
16. incomplete recovery returns its retained path;
17. outcome invariants reject contradictory state.

`UpdateDataFiles` retains only focused Swing adapter tests. Existing `AppBootstrap` tests continue to specify when a
refresh is scheduled and verify that the same application-owned module reaches the background-job adapter.

## Delivery constraints

- Preserve the `beans` and `io` prohibition on Swing and AWT imports enforced by `ArchitectureTest`.
- Add an architecture assertion that the deep GameData Refresh source closure does not import `ui`.
- Keep user-visible logging behavior equivalent except for the additional diagnostic evidence agreed above.
- A green `mvn clean test` is required.
- This design does not require a new ADR. ADR-0001 continues to own data-directory resolution; the new module follows
  that accepted decision rather than changing it.
