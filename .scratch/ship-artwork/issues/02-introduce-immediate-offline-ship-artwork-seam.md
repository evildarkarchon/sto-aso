Status: resolved
Blocked by: 01

# 02: Introduce the immediate offline Ship Artwork seam

## What to build

Provide the deep Ship Artwork interface through which a caller supplies a canonical Ship and a named generic or specific presentation and immediately receives stable, fixed-size artwork. Keep existing application callers working through a temporary migration bridge while the new seam expands beside them.

## Acceptance criteria

- [x] Opening Ship Artwork accepts the resolved data directory, one GameData instance, and initial current-Roster Ship types without exposing storage or execution details.
- [x] Lookup accepts only canonical Ships from the supplied GameData and rejects null, foreign, or invalid presentation arguments before changing state or starting work.
- [x] Generic presentation is composed only from required bundled resources and never initiates remote acquisition.
- [x] Specific presentation uses current or bundled specific pixels when available and otherwise returns generic artwork immediately.
- [x] Repeated equivalent lookups return the same non-null, fixed-size, read-only image-handle identity.
- [x] Missing or unreadable required composition resources fail module construction with a useful diagnostic.
- [x] Existing callers remain green through an explicitly temporary migration bridge.

## Comments

Implemented the offline `ui.artwork.ShipArtwork` creation, lookup and close seam.
Canonical membership uses reference identity; the entire initial Roster is
validated before resource work. Construction decodes required composition assets
and available bundled sources, so lookup performs no filesystem or network I/O.
Specific presentation uses the in-memory bundled composition or immediate generic
fallback. Archive loading and remote acquisition remain in their later tickets.

Stable 64 × 64 handles reject ImageIcon setters and return defensive image
snapshots. Close is idempotent and rejects subsequent lookup without invalidating
already-returned pixels. The existing factories are explicitly documented as the
temporary bridge; ownership and caller cutover remain in tickets 13–16, followed
by removal in ticket 17.

Tests cover all 210 generic baseline combinations, bundled shuttle pixels,
stable identity, canonical validation, read-only handles, resource-free lookup,
optional corrupt-source fallback, and missing/unreadable failures for all 33
required assets. Focused artwork, existing Swing presentation and architecture
checks passed. Standards and spec reviews have no remaining findings; the review's
use-after-close inconsistency was corrected with a failing regression test.

Final verification: `.\gradlew.bat clean build` passed on Java 25, including the
complete test suite and thin-JAR, exploded and packaged bootstrap verification.
Existing comments were preserved; migration-bridge Javadocs were added.
