Status: ready-for-agent
Blocked by: 13

# 14: Migrate shared renderers and Ship Filter presentation

## What to build

Move the shared Swing rendering and Ship Filter path from storage-shaped icon requests to canonical Ship Artwork requests while retaining existing filtering, ordering, selection, details, and card appearance.

## Acceptance criteria

- [ ] Shared Ship, Roster-card, Starship Trait, usage, and assignment renderers request artwork with a canonical Ship and a named presentation.
- [ ] Ship Filter construction passes the Ship Artwork dependency through to list rendering and Ship details without process-global lookup.
- [ ] Generic GameData and details presentations never initiate network work.
- [ ] Reusable and One-Time presentation decisions remain explicit rather than being represented by an ownership boolean.
- [ ] Live replacement preserves Ship Filter criteria, visible ordering, selected identities, and details layout.
- [ ] Renderer and Ship Filter tests use the Ship Artwork seam rather than the temporary factory interface.
