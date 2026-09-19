Status: resolved
Blocked by:

# 01: Characterize current Ship Artwork and legacy archives

## What to build

Capture the current generic and specific Ship Artwork appearance and representative legacy archive behavior before replacing the implementation, so later migration stages can prove that established presentations and useful legacy pixels were preserved.

## Acceptance criteria

- [x] Automated characterization covers generic and specific artwork across representative faction, role, and rarity combinations.
- [x] Visual baselines cover reusable Roster cards, One-Time Ships, Starship Traits, selection dialogs, Ship usage, and Solution cards.
- [x] Fixture archives represent recognizable, unmatched, and unreadable legacy entries without depending on user state or network access.
- [x] The characterization records fixed artwork dimensions and the current composition ordering needed by later tests.
- [x] Existing production behavior remains unchanged and the complete test suite remains green.

## Comments

Resolved 2026-09-18. Added 420 frozen composition comparisons across every
faction/role/rarity combination, plus bundled lookup, missing-image fallback,
cache precedence and One-Time presentation checks. Nine visually inspected
headless Swing captures cover all requested presentations; separate surface
tests check their generic/specific artwork policies without brittle font
comparisons. Three immutable legacy ZIP fixtures characterize recognizable,
unmatched and unreadable entries using temporary archive copies.

[Baseline documentation](../../../test/resources/ship-artwork/README.md)
records the 64 × 64 dimensions, composition ordering, provenance, regeneration
commands and limitations. The current destructive legacy recovery is captured
as historical behavior, not endorsed for the future migration. Production code
and bundled assets are unchanged.

Verification: focused composition, archive and surface tests passed; both
capture tasks ran successfully; composition regeneration was byte-identical;
`./gradlew.bat clean build` passed with Java 25, including the complete test
suite and thin-JAR/exploded/packaged bootstrap checks. Standards and spec code
reviews reported no actionable findings.
