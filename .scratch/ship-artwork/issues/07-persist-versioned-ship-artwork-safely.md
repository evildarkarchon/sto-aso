Status: ready-for-agent
Blocked by: 05

# 07: Persist versioned Ship Artwork safely

## What to build

Persist reusable Ship Artwork in a separate v2 archive whose identities describe what produced the pixels, and install only complete replacement archives without endangering the last working state.

## Acceptance criteria

- [ ] The v2 format records schema and recipe versions, source-image identity, every canonical Ship fact used by composition, per-image successful freshness, and integrity evidence.
- [ ] Saving and reopening preserves valid artwork, identity, freshness, and integrity relationships through the public Ship Artwork seam.
- [ ] Legacy `icons.zip` is never overwritten or used as the v2 destination.
- [ ] Installation prefers atomic replacement and falls back to replacing with an already completed archive when atomic replacement is unavailable.
- [ ] A write or installation failure preserves the previous valid v2 archive.
- [ ] Filesystem tests use temporary directories and a narrow fault adapter only where a real replacement failure cannot be induced reliably.
