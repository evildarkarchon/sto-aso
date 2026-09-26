# Ship Artwork seam — design decisions

Confirmed during the architecture review and grilling session on 2026-09-18.
This document records the agreed design for a later implementation change; the
production migration has not yet been performed.

Vocabulary follows `CONTEXT.md` and `/codebase-design`: module, interface,
implementation, depth, seam, adapter, leverage, locality, Ship Artwork, Icon
Cache, Ship, GameData and Roster.

## Problem and evidence

- The current Icon Cache lifecycle is split across `AppBootstrap`, `App`,
  `IconCache`, `ActualShipIconFactory`, `GenericShipIconFactory`,
  `SwingWorkerExecutor`, `ShipIconLoader` and `AdmiraltyConsole`.
- A caller coordinates cache membership, bundled fallback, composition,
  background acquisition and shutdown persistence through storage-shaped
  operations.
- `ShipIconLoader` reaches through static `App` state for both GameData and the
  Icon Cache. It has no focused test surface.
- Cache eligibility is checked outside `IconCache`, so concurrent requests can
  schedule duplicate downloads.
- The existing seven-day freshness check touches `icons.zip` when work is
  scheduled, before acquisition succeeds. Existing entries are then skipped by
  the downloader, so persisted artwork is not actually refreshed.
- Persisted entries are keyed only by image filename even though composed Ship
  Artwork also depends on canonical faction, role, rarity and the composition
  recipe.
- The current factory interface exposes an icon filename, four presentation
  facts and an ownership boolean. Its interface is nearly the composition and
  cache implementation.

The deletion test favors a deep Ship Artwork module. Removing the current
loaders and factories mostly removes indirection. Removing the proposed module
would redistribute acquisition, fallback, composition, repaint, freshness,
retry, migration and persistence across bootstrap, renderers, workers and
tooling.

## Domain meaning

`CONTEXT.md` defines **Ship Artwork** as the composed visual shown on a Ship card
and **Icon Cache** as the locally persisted set of composed Ship Artwork. Ship
Artwork is the caller-facing concept. The Icon Cache becomes internal persisted
state rather than a caller-visible module.

Generic Ship Artwork is a deliberate presentation derived from canonical Ship
facts. It is used for One-Time Ship presentation and as the fallback when
specific Ship artwork is unavailable.

## Change envelope

- Preserve current card dimensions, composition, generic One-Time Ship
  presentation, GitHub source and seven-day remote freshness cadence.
- Preserve nonfatal startup when optional derived artwork is unavailable.
- Correct duplicate acquisition, failed-refresh suppression, interruption,
  stale composite identity and absent live repaint behavior.
- A completed remote acquisition updates visible artwork without rebuilding a
  Ship Filter, changing selection or reopening a window.
- GameData, Roster, Solution, assignment, Admirals XML and GameData Refresh
  behavior remain unchanged.
- ADR-0001 continues to own data-directory resolution. Ship Artwork receives the
  already resolved directory.

## Deep module and external seam

One application-owned `ui.artwork.ShipArtwork` module is created after GameData
loads and before Swing frames are constructed. Its application interface has
three entry points including creation:

```java
ShipArtwork.open(dataDirectory, gameData, initialRosterShips);
ImageIcon forShip(Ship ship, Presentation presentation);
void close();
```

`Presentation` has two values:

- `SPECIFIC` requests Ship-specific artwork with generic fallback;
- `GENERIC` requests canonical generic artwork and never initiates network work.

`forShip` accepts a canonical Ship belonging to the GameData supplied at open.
Foreign Ship values and null arguments are programming errors rejected before
work starts. The call performs no filesystem or network I/O and returns a
non-null, fixed-size, read-only `ImageIcon` immediately.

The returned `ImageIcon` is a stable live handle. Its object identity remains
constant while its private delegate can advance from generic or stale pixels to
fresh specific pixels. Callers must not mutate it. The implementation weakly
tracks useful Swing paint owners and coalesces targeted repaint requests. Pixel
replacement and repaint occur on Swing's event-dispatch thread. Weak tracking
must not retain disposed windows or views.

The interface deliberately exposes no listener registration, cache key,
freshness query, executor, acquisition future, archive operation or migration
operation. Alternative listener, portfolio/snapshot and public batch-operation
interfaces were rejected because they made callers learn implementation
protocol that the module can hide. See ADR-0003.

## Resolution and acquisition behavior

`GENERIC` returns artwork composed entirely from required bundled composition
assets and canonical Ship facts.

`SPECIFIC` returns the first available choice in this order:

1. current versioned specific Ship Artwork;
2. freshly composed bundled Ship Artwork;
3. stale last-known-good v2 or migrated legacy artwork;
4. generic Ship Artwork.

A missing or stale remote image starts or joins asynchronous acquisition when
backoff permits. Startup prewarms current-Roster Ship types; later specific
requests acquire missing artwork on demand. Concurrent startup and on-demand
requests for one internal image identity share one attempt.

Freshness is recorded per successfully acquired remote image. Bundled images
follow the application version and do not use remote freshness. Partial failure
does not mark failed or unrelated images fresh.

Acquisition failure retains last-known-good artwork when present and otherwise
retains generic artwork. It logs one diagnostic for the coalesced attempt, never
interrupts startup and never shows a modal error.

At most three remote acquisitions run concurrently. A failed image backs off
for one minute, then five minutes, then thirty minutes. Success resets its
sequence. Explicit online tooling and the next application launch bypass
in-process backoff.

## Remote source and composition assets

GitHub is a true external dependency. A private acquisition port has:

- a production GitHub HTTPS adapter;
- a deterministic scripted test adapter;
- an offline tooling adapter that fails if offline work attempts network use.

Two adapters make the seam real, but it remains internal so callers do not
choose transports.

The production adapter uses the fixed GitHub source, a five-second connection
timeout, a fifteen-second request timeout, a two-MiB response limit and maximum
decoded dimensions of 2048 by 2048. Redirects are accepted only between
allowlisted GitHub HTTPS origins. Invalid content types, incomplete decoding,
oversized content, invalid dimensions and unexpected redirects are operational
acquisition failures.

Faction backgrounds and role and rarity frames are required bundled resources.
Ship Artwork construction fails loudly if a required composition resource is
missing or unreadable. Only the Ship-specific source image may be acquired from
GitHub.

## Identity and persistence

Callers never create or inspect cache keys. Internal versioned identity includes:

- source-image identity;
- canonical Ship presentation facts used in composition;
- the Ship Artwork recipe version.

The v2 archive is separate from legacy `icons.zip`. Legacy `icons.zip` is
read-only migration input and remains available for rollback. Older application
versions therefore cannot interpret or delete v2 metadata, and the new
application never rewrites legacy state implicitly.

The v2 archive records schema and recipe versions, per-entry identity,
successful remote freshness and integrity evidence. Writes create a complete
temporary archive and install it atomically where supported, with completed-file
replacement fallback. Installation failure preserves the prior v2 archive.

On-demand successes are persisted two seconds after the last success, with a
thirty-second maximum delay while fills continue. A startup prewarm batch
persists once after completion. Shutdown performs a final flush after the
bounded close protocol.

A corrupt v2 archive is quarantined under a recovery filename before the module
continues with legacy or generic artwork and schedules rebuilding. If quarantine
fails, the unreadable archive is left untouched, the failure is logged and
in-memory Ship Artwork remains available. Legacy `icons.zip` is never mutated by
recovery.

## Legacy migration

The current GameData has a unique legacy icon filename for every canonical Ship,
so current legacy entries can be mapped unambiguously. Legacy pixels do not
carry source identity or recipe metadata and therefore enter v2 only as stale
last-known-good artwork. They never satisfy a current versioned identity without
successful recomposition or reacquisition.

Unknown, removed, unreadable or otherwise unmappable legacy entries are reported
with reasons and omitted from the v2 primary archive. They remain recoverable
from the untouched legacy archive.

Automatic application migration is lazy. Ordinary users need no operator step.
The first successful v2 persistence writes the new format without destroying
legacy state.

## Tooling adapter

A headless `ShipArtworkTool` adapter and Gradle `JavaExec` task use the same Ship
Artwork implementation, identity rules, migration matcher, validator and atomic
writer as the application. Tooling requires an explicit `--data-directory <path>` and
never applies ADR-0001 directory inference.

Supported operations are:

- `inspect`: read-only legacy and v2 inventory;
- `migrate`: deterministic offline migration by default;
- `migrate --online-refresh`: explicit GitHub reacquisition;
- `verify`: read and validate every v2 entry, digest, identity and metadata
  relationship without writing;
- `cleanup --confirm-legacy-cleanup`: remove only the explicitly targeted legacy archive after
  successful v2 verification and an explicit confirmation flag.

The default report is concise human-readable text. `--json` emits the same
structured outcome. Invalid arguments, verification findings and operational
failure use distinct nonzero exit statuses. Unmatched legacy entries are
verification findings rather than silent success.

Offline operations use an adapter that makes any accidental network attempt a
testable failure. Inspect and verify never write. Migration and cleanup resolve
and validate their exact target before mutation.

## Lifetime and shutdown

The application owns exactly one Ship Artwork instance. Bootstrap opens it,
renderers receive it through ownership seams, and application shutdown closes
it. Static `App` lookup is not used by Ship Artwork, acquisition or rendering.

Close is idempotent. It stops accepting acquisition requests, grants active work
a short bounded completion period, cancels the remainder, and atomically flushes
successfully completed artwork. Cancelled work does not advance freshness and
is due next launch. Network work can never block shutdown indefinitely.

Interruption is preserved. Operational persistence failure is logged and
reported where an outcome is available, preserves the previous archive and does
not prevent application exit.

## Internal seams and adapters

- Composition, fallback selection, identity, coalescing and backoff are
  in-process implementation and need no adapter.
- Archive and legacy filesystem behavior are local-substitutable and are tested
  with real temporary directories. A narrow internal install seam has production
  and fault-injection adapters for replacement failures that cannot be induced
  reliably.
- Time, delayed work and execution have production and deterministic test
  adapters so freshness, backoff, debounce, cancellation and ordering are
  testable through the external interface.
- Swing dispatch has production event-thread and deterministic test adapters.
- GitHub acquisition uses the internal production and scripted adapters described
  above.

None of these internal seams appears in the external Ship Artwork interface.
The interface is the test surface.

## Compatibility and contraction

After every caller migrates, delete these shallow modules rather than retaining
forwarding or deprecated interfaces:

- `ui.resources.ShipIconFactory`;
- `ui.resources.GenericShipIconFactory`;
- `ui.resources.ActualShipIconFactory`;
- `ui.resources.IconCache`;
- `ui.workers.ShipIconLoader`.

Remove icon-download eligibility and scheduling from `SwingWorkerExecutor` and
`AppBootstrap`. Remove Icon Cache storage access from static `App` state.
Bootstrap supplies initial current-Roster Ships directly when opening Ship
Artwork. Shutdown closes Ship Artwork rather than calling Icon Cache persistence.

No compatibility adapter remains in the final state. A temporary internal
migration stage may exist only while the branch remains green and must be
deleted before completion.

## Test strategy

Replace tests shaped around storage primitives, loaders and factories with tests
through `ShipArtwork.open`, `forShip`, `close` and the tooling adapter. Cover:

1. immediate generic fallback and fixed dimensions;
2. bundled specific artwork without network access;
3. stable handle identity and read-only caller contract;
4. event-thread delegate replacement and targeted repaint;
5. unchanged Ship Filter projection and selection after repaint;
6. startup and on-demand acquisition coalescing;
7. three-request concurrency limit;
8. acquisition time, size, origin, content and dimension validation;
9. failure retaining last-known-good or generic artwork;
10. one diagnostic per coalesced failure;
11. one-, five- and thirty-minute backoff and reset behavior;
12. per-image successful freshness;
13. debounced, maximum-delay, startup-batch and final persistence;
14. bounded close, cancellation and interruption preservation;
15. atomic replacement fallback and preservation after failure;
16. corrupt v2 quarantine and quarantine failure;
17. offline legacy migration, unmatched reporting and untouched legacy state;
18. optional online migration and explicit cleanup;
19. inspect and verify read-only behavior;
20. human and JSON reports plus stable exit-status categories;
21. accidental network use failing offline tooling;
22. restart round-trip through the v2 archive;
23. null, foreign Ship and use-after-close programming errors.

Retain visual baseline coverage for Roster, One-Time Ship, Starship Trait,
selection, usage and Solution presentation. Add architecture guards that the
retired declarations cannot return and only the intended Ship Artwork seam is
public. Old tests that exercise implementation beneath that seam are replaced,
not layered under the new interface tests.

## Delivery sequence

Deliver one complete migration through green stages:

1. characterize current composed artwork and representative legacy archives;
2. add Ship Artwork composition and stable handles;
3. add scripted acquisition, coalescing, freshness, backoff and repaint;
4. add v2 persistence, recovery, migration, verification and Gradle tooling;
5. migrate bootstrap, renderers, shutdown and entrypoints;
6. delete the retired modules and replace implementation-shaped tests;
7. run `.\gradlew.bat clean build`, offline tooling checks, visual baselines and
   a manual online refresh.

The final verification record distinguishes automated checks, offline tooling
evidence, visual baselines and the actual manual online refresh. No manual result
is inferred from automated coverage.

## Decisions deliberately outside this change

- No non-Swing Ship Artwork interface is introduced.
- No configurable remote origin or caller-supplied acquisition adapter is added
  to the external interface.
- No GameData live reload or artwork update after a GameData Refresh is added;
  refreshed GameData remains next-launch behavior.
- No general-purpose download manager or filesystem abstraction is introduced.
- No automatic deletion of legacy `icons.zip` occurs.
