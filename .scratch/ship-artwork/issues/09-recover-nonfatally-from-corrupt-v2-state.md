Status: ready-for-agent
Blocked by: 07

# 09: Recover nonfatally from corrupt v2 state

## What to build

Keep optional Ship Artwork from becoming a startup requirement when the v2 archive is unreadable, while retaining useful diagnostic evidence whenever the local filesystem permits it.

## Acceptance criteria

- [ ] Invalid schema, metadata, identity, digest, image, and archive structure are recognized as corrupt v2 state.
- [ ] Corrupt state is moved to a recovery name when possible before a new v2 state is built.
- [ ] If quarantine fails, the unreadable archive remains untouched and the failure is logged.
- [ ] Both successful and failed quarantine paths continue with legacy or generic artwork rather than failing application startup.
- [ ] Recovery never mutates the legacy archive.
- [ ] Restart-visible tests cover quarantine, quarantine failure, and successful rebuilding.
