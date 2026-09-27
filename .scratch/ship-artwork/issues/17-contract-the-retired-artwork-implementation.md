Status: resolved
Blocked by: 13, 15, 16

# 17: Contract the retired artwork implementation

## What to build

Finish the expand-contract migration by removing every obsolete artwork factory, caller-visible Icon Cache operation, background icon loader, and temporary compatibility path so Ship Artwork is the only supported application seam.

## Acceptance criteria

- [x] The old artwork factory interface, generic factory, production factory, caller-visible Icon Cache module, background icon loader, and temporary migration bridge are deleted.
- [x] No production or test source refers to retired declarations, icon-download scheduling, filename-based caller keys, or direct Icon Cache operations.
- [x] Implementation-shaped cache and loader tests are replaced by behavior tests at the Ship Artwork and operator seams.
- [x] Architecture checks reject retired declarations anywhere in production source.
- [x] Architecture checks permit private implementation changes while enforcing Ship Artwork as the module's only public application seam.
- [x] GameData Refresh and the general Swing worker functionality it still needs remain intact.

## Comments

Resolved 2026-09-26. Removed the unused factory and cache declarations, bridge, cache filename constants, and tests bound to direct cache operations. The background icon loader was already absent; GameData Refresh and the general Swing worker executor remain unchanged. Added architecture guards for retired declarations and the public Ship Artwork seam, plus an existing-archive replacement regression at the Ship Artwork seam. `clean build` passed, and the offline operator tool successfully inspected, migrated, and verified a temporary legacy fixture while preserving its source archive.
