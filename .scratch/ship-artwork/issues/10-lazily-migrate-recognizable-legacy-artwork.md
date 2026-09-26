Status: resolved
Blocked by: 01, 07

# 10: Lazily migrate recognizable legacy artwork

## What to build

Reuse recognizable legacy pixels automatically as stale last-known-good Ship Artwork while preserving the original archive for rollback and making every omitted entry explainable.

## Acceptance criteria

- [x] Legacy filenames are mapped only to unambiguous canonical Ships from the supplied GameData.
- [x] Recognizable legacy pixels are available immediately as stale last-known-good artwork and never satisfy a current versioned identity by themselves.
- [x] Unknown, removed, unreadable, and otherwise unmappable entries are omitted from v2 with a recorded reason.
- [x] Matched, migrated, unreadable, and unmatched counts and details are available as one structured migration outcome.
- [x] Automatic migration performs no remote acquisition merely because legacy state exists.
- [x] The original legacy archive remains byte-for-byte untouched after successful or failed lazy migration.

## Comments

Resolved 2026-09-25. Ship Artwork now reads `icons.zip` without modifying it, maps exact
filenames only when the supplied GameData identifies one canonical Ship, and stores
recognized composed pixels in a separate stale section of `ship-artwork-v2.zip`.
Legacy entries have neither current recipe identity nor successful remote freshness;
lookup still requests a current image when needed. Removed or newly ambiguous Ships
are pruned from persisted stale state on a later launch.

One structured outcome records the matched, newly admitted, unreadable, and unmatched
counts plus per-entry reasons; archive-level read errors are also recorded. "Migrated"
counts pixels admitted as stale fallback in the current lifetime; v2 persistence may
finish later or fail independently. Tests cover frozen pixels, restart behavior,
omission reasons, malformed input, current-artwork precedence, and unchanged legacy
bytes after successful and failed writes. The focused Ship Artwork suite and
`.\gradlew.bat clean build` passed. Standards and spec reviews found no remaining
actionable issues after documentation fixes.
