Status: resolved
Blocked by: 07

# 09: Recover nonfatally from corrupt v2 state

## What to build

Keep optional Ship Artwork from becoming a startup requirement when the v2 archive is unreadable, while retaining useful diagnostic evidence whenever the local filesystem permits it.

## Acceptance criteria

- [x] Invalid schema, metadata, identity, digest, image, and archive structure are recognized as corrupt v2 state.
- [x] Corrupt state is moved to a recovery name when possible before a new v2 state is built.
- [x] If quarantine fails, the unreadable archive remains untouched and the failure is logged.
- [x] Both successful and failed quarantine paths continue with legacy or generic artwork rather than failing application startup.
- [x] Recovery never mutates the legacy archive.
- [x] Restart-visible tests cover quarantine, quarantine failure, and successful rebuilding.

## Comments

Resolved 2026-09-20. Corrupt v2 state is moved to a unique
`ship-artwork-v2.zip.corrupt-<UUID>` recovery name before acquisition can rebuild
it. If the move fails, the failure is logged and all persistence is disabled for
that lifetime; generic artwork and successful in-memory acquisition remain
available without overwriting the unreadable archive. Legacy `icons.zip` is
never changed by recovery.

Archive validation now rejects duplicate or unexpected ZIP entries and shares
the existing strict PNG decoder with remote acquisition, including checks for
complete PNG framing and compressed pixels. Existing schema, recipe, metadata,
identity and digest validation continues to reject the entire invalid state.

Thirteen recovery cases through creation, lookup and close cover corrupt-state
categories, original recovery bytes, quarantine failure diagnostics, unchanged
legacy bytes, in-memory success, repeated launches and successful rebuilding
visible after restart. Focused recovery, persistence and HTTP tests passed, as
did `.\gradlew.bat clean build` on Java 25 (808 tests, no failures or skips).
Independent standards and spec reviews found no issues. Updated archive-move
and constructor documentation to reflect quarantine; existing PNG validation
comments moved with their code.
