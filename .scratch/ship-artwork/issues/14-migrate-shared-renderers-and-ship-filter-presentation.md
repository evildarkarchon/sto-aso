Status: resolved
Blocked by: 13

# 14: Migrate shared renderers and Ship Filter presentation

## What to build

Move the shared Swing rendering and Ship Filter path from storage-shaped icon requests to canonical Ship Artwork requests while retaining existing filtering, ordering, selection, details, and card appearance.

## Acceptance criteria

- [x] Shared Ship, Roster-card, Starship Trait, usage, and assignment renderers request artwork with a canonical Ship and a named presentation.
- [x] Ship Filter construction passes the Ship Artwork dependency through to list rendering and Ship details without process-global lookup.
- [x] Generic GameData and details presentations never initiate network work.
- [x] Reusable and One-Time presentation decisions remain explicit rather than being represented by an ownership boolean.
- [x] Live replacement preserves Ship Filter criteria, visible ordering, selected identities, and details layout.
- [x] Renderer and Ship Filter tests use the Ship Artwork seam rather than the temporary factory interface.

## Comments

Resolved 2026-09-26. Shared renderers and all named Ship Filter views now pass
canonical Ships and explicit `GENERIC` or `SPECIFIC` presentation to the
application-owned Ship Artwork instance. Ship details and GameData paths stay
generic. The constructor dependency was carried through the workspace and
standalone presentation roots so no active renderer uses the temporary factory.

Renderer, Ship Filter, usage, workspace, and live-replacement tests use real
offline or scripted Ship Artwork. The live test retains filter criteria, visible
order, selected identities, list models, and the two-column details layout.
All nine generated headless view PNGs matched the checked-in baselines byte for
byte. `.\gradlew.bat clean build` passed all 842 JUnit tests, architecture checks,
and packaged-entry verification. No online refresh was performed for this
presentation-only ticket.
