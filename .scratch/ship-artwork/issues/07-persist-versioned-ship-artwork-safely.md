Status: resolved
Blocked by: 05

# 07: Persist versioned Ship Artwork safely

## What to build

Persist reusable Ship Artwork in a separate v2 archive whose identities describe what produced the pixels, and install only complete replacement archives without endangering the last working state.

## Acceptance criteria

- [x] The v2 format records schema and recipe versions, source-image identity, every canonical Ship fact used by composition, per-image successful freshness, and integrity evidence.
- [x] Saving and reopening preserves valid artwork, identity, freshness, and integrity relationships through the public Ship Artwork seam.
- [x] Legacy `icons.zip` is never overwritten or used as the v2 destination.
- [x] Installation prefers atomic replacement and falls back to replacing with an already completed archive when atomic replacement is unavailable.
- [x] A write or installation failure preserves the previous valid v2 archive.
- [x] Filesystem tests use temporary directories and a narrow fault adapter only where a real replacement failure cannot be induced reliably.

## Comments

Resolved 2026-09-19. Ship Artwork now persists completed remote compositions in
`ship-artwork-v2.zip` with a deterministic, SHA-256-protected manifest and one
digest per PNG. Versioned identities include the source image, faction, role,
rarity and recipe version; successful freshness remains source-specific, and a
launch-time refresh-due bit preserves retry behavior without persisting backoff.
Reopening reuses only an exact valid identity while stale last-known-good pixels
remain visible during refresh.

Archive writes finish in `ship-artwork-v2.zip.new` before installation. Atomic
replacement is preferred; the non-atomic fallback snapshots the prior archive,
restores it after even a destructive provider failure, and retains the backup if
restoration itself is unavailable. Legacy `icons.zip` is never read or written by
the v2 persistence path.

Six temporary-filesystem seam tests cover round-trip pixels and freshness,
metadata and integrity evidence, canonical-fact mismatch, atomic fallback, real
replacement-write obstruction and destructive installation failure. The complete
Ship Artwork package suite and `.\gradlew.bat clean build` passed. Independent
standards and spec reviews reported no remaining findings after rollback and
documentation fixes.
