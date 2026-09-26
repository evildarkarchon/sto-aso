Status: ready-for-agent
Blocked by: 01, 07

# 10: Lazily migrate recognizable legacy artwork

## What to build

Reuse recognizable legacy pixels automatically as stale last-known-good Ship Artwork while preserving the original archive for rollback and making every omitted entry explainable.

## Acceptance criteria

- [ ] Legacy filenames are mapped only to unambiguous canonical Ships from the supplied GameData.
- [ ] Recognizable legacy pixels are available immediately as stale last-known-good artwork and never satisfy a current versioned identity by themselves.
- [ ] Unknown, removed, unreadable, and otherwise unmappable entries are omitted from v2 with a recorded reason.
- [ ] Matched, migrated, unreadable, and unmatched counts and details are available as one structured migration outcome.
- [ ] Automatic migration performs no remote acquisition merely because legacy state exists.
- [ ] The original legacy archive remains byte-for-byte untouched after successful or failed lazy migration.
