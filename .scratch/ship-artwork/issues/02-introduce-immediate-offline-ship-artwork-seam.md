Status: ready-for-agent
Blocked by: 01

# 02: Introduce the immediate offline Ship Artwork seam

## What to build

Provide the deep Ship Artwork interface through which a caller supplies a canonical Ship and a named generic or specific presentation and immediately receives stable, fixed-size artwork. Keep existing application callers working through a temporary migration bridge while the new seam expands beside them.

## Acceptance criteria

- [ ] Opening Ship Artwork accepts the resolved data directory, one GameData instance, and initial current-Roster Ship types without exposing storage or execution details.
- [ ] Lookup accepts only canonical Ships from the supplied GameData and rejects null, foreign, or invalid presentation arguments before changing state or starting work.
- [ ] Generic presentation is composed only from required bundled resources and never initiates remote acquisition.
- [ ] Specific presentation uses current or bundled specific pixels when available and otherwise returns generic artwork immediately.
- [ ] Repeated equivalent lookups return the same non-null, fixed-size, read-only image-handle identity.
- [ ] Missing or unreadable required composition resources fail module construction with a useful diagnostic.
- [ ] Existing callers remain green through an explicitly temporary migration bridge.
