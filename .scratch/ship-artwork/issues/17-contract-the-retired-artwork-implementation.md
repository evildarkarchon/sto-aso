Status: ready-for-agent
Blocked by: 13, 15, 16

# 17: Contract the retired artwork implementation

## What to build

Finish the expand-contract migration by removing every obsolete artwork factory, caller-visible Icon Cache operation, background icon loader, and temporary compatibility path so Ship Artwork is the only supported application seam.

## Acceptance criteria

- [ ] The old artwork factory interface, generic factory, production factory, caller-visible Icon Cache module, background icon loader, and temporary migration bridge are deleted.
- [ ] No production or test source refers to retired declarations, icon-download scheduling, filename-based caller keys, or direct Icon Cache operations.
- [ ] Implementation-shaped cache and loader tests are replaced by behavior tests at the Ship Artwork and operator seams.
- [ ] Architecture checks reject retired declarations anywhere in production source.
- [ ] Architecture checks permit private implementation changes while enforcing Ship Artwork as the module's only public application seam.
- [ ] GameData Refresh and the general Swing worker functionality it still needs remain intact.
