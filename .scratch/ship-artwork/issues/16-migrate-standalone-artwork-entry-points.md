Status: ready-for-agent
Blocked by: 14

# 16: Migrate standalone artwork entry points

## What to build

Bring the Ship usage, Trait Viewer, diagnostic, build-probe, and visual-baseline entry points onto the same owned Ship Artwork lifecycle so alternate application roots neither create competing modules nor omit cleanup.

## Acceptance criteria

- [ ] Ship usage and Trait Viewer obtain and pass one Ship Artwork instance rather than constructing production factories.
- [ ] Standalone roots close their owned module idempotently when their lifetime ends.
- [ ] Usage rows request specific presentation only for Ships in the current Roster and generic presentation otherwise.
- [ ] Trait presentations retain their accepted generic or Roster-specific semantics.
- [ ] Diagnostic, build-probe, and visual-baseline harnesses model the new bootstrap and lifetime contract without network or user-state dependencies.
- [ ] Standalone behavior, build-artifact verification, and retained visual baselines remain green.
