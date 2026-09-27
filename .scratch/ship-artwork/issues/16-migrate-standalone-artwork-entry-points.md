Status: resolved
Blocked by: 14

# 16: Migrate standalone artwork entry points

## What to build

Bring the Ship usage, Trait Viewer, diagnostic, build-probe, and visual-baseline entry points onto the same owned Ship Artwork lifecycle so alternate application roots neither create competing modules nor omit cleanup.

## Acceptance criteria

- [x] Ship usage and Trait Viewer obtain and pass one Ship Artwork instance rather than constructing production factories.
- [x] Standalone roots close their owned module idempotently when their lifetime ends.
- [x] Usage rows request specific presentation only for Ships in the current Roster and generic presentation otherwise.
- [x] Trait presentations retain their accepted generic or Roster-specific semantics.
- [x] Diagnostic, build-probe, and visual-baseline harnesses model the new bootstrap and lifetime contract without network or user-state dependencies.
- [x] Standalone behavior, build-artifact verification, and retained visual baselines remain green.

## Comments

Resolved 2026-09-26. Ship usage and Trait Viewer now carry the one bootstrapped
Ship Artwork instance into their standalone frames and close it on window exit or
failed frame creation. The diagnostic, build probe, and both visual capture
entry points close their owned lifetimes. Capture tools keep artwork state in
fresh temporary directories, separate from PNG destinations and user data.

Usage rows retain specific artwork for reusable current-Roster Ships and generic
artwork for historical and One-Time rows. Trait presentation remains generic
for GameData and specific where accepted for reusable Roster cards. The
historical direct-helper `specific.png` remains untouched; the module's
production-equivalent smooth scaling is recorded in `specific-smooth.png`.

Focused standalone, diagnostic, characterization, and view tests passed.
The native passive smoke task passed, generated view PNGs matched all nine
retained baselines byte for byte, and `.\gradlew.bat clean build` passed the
complete suite plus exploded, packaged, and thin-JAR verification. Automated
artwork acquisition remained offline; no online refresh was performed.
