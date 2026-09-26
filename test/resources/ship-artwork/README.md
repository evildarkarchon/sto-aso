# Ship Artwork characterization (before migration)

Captured from production revision `88eda23d05579f58838ad73237660b39cfecc61c`
on 2026-09-18 using Eclipse Temurin Java 25.0.4.1 on Windows. This is issue 01's
record of existing behavior, not the contract for the later archive migration.
No production code or bundled artwork was changed to produce these fixtures.

## Evidence

- [Composition PNGs](composition/): fixed pixel expectations checked by
  `ShipArtworkCharacterizationTest` (420 combinations plus lookup/presentation cases).
- [View PNGs and capture notes](views/README.md): artwork in the real Swing
  presentations, with environment-sensitive text/layout kept as visual evidence.
- [Legacy archives and entry provenance](legacy/README.md): offline inputs for
  archive characterization and later migration tests. Tests copy these into temporary
  directories before calling the current destructive loader.

## Dimensions and composition

Every composed image is **64 × 64 ARGB pixels**. The atlases are **1920 × 448**,
with 30 columns and seven rows of 64-pixel tiles, with no gutters.

Columns are grouped by faction: `None`, `Federation`, `Klingon`, `Romulan`,
`JemHadar`, `Universal`. Within each faction the five columns are `None`, `Eng`,
`Sci`, `Tac`, `Smc`. Rows are `None`, `Common`, `Uncommon`, `Rare`, `VeryRare`,
`UltraRare`, `Epic`. These orders are explicit in the test helper, independent of
future enum declaration order.

`generic.png` records the current generic recipe:

1. Draw the faction background at (0, 0) at its native size. `None` and
   `Universal` use the Federation background.
2. Classified factions draw their faction/role emblem at (0, 0); role `None`
   adds nothing. For `None` and `Universal`, draw `lobi.png` at (0, 0), then
   `eng.png`, `sci.png`, or `tac.png` at (1, 1). `Smc` and `None` add no role
   emblem in these two factions.
3. **Rarity does not change generic pixels**, despite participating in the old
   in-memory cache key. There is no generic rarity frame.

`specific.png` records the current specific recipe:

1. Draw the faction background scaled to 64 × 64. `None` and `Universal` use
   the Federation background.
2. Draw the source image over the background, filling 64 × 64.
3. Draw the role frame over the source. `None` adds no frame.
4. Draw the rarity frame last. `None` and `Common` add no rarity frame.

Role and rarity frame resources are first resampled to 64 × 64 using bicubic
interpolation and antialiasing. The bundled lookup path additionally uses
`Image.SCALE_SMOOTH` for the Ship source before composition.
`bundled-shuttle.png` separately records that path for `Class_F_Shuttle.png`,
Federation / Smc / Common.

`source.png` is a synthetic **97 × 83** image: a coordinate color ramp with
transparent, half-alpha and opaque vertical bands. Its deliberately non-square
dimensions and visible background make scaling, alpha composition, and frame
ordering observable without remote acquisition. It is not a game asset.

Specific lookup currently prefers an existing composed cache entry, then bundled
source artwork, then generic fallback. An unowned/One-Time request always returns
generic artwork, even if specific pixels are already cached. Existing cache keys
are filenames alone; neither these tests nor the archive fixtures imply that the
legacy format has versioned composition identity.

## Verification and regeneration

```powershell
.\gradlew.bat test --tests 'com.kor.admiralty.ui.resources.ShipArtworkCharacterizationTest'
.\gradlew.bat shipArtworkCompositionBaseline
```

The capture task writes candidates to `build/ship-artwork-composition` by default;
an explicit `--args='<output-directory>'` selects another destination. Normal tests
only read committed PNGs and compare decoded ARGB pixels, never PNG encoder bytes.
Review candidate images before replacing historical baselines. During migration,
adapt the invocation to the new public Ship Artwork seam while retaining these
expected pixels; do not regenerate expectations merely to make tests pass.

The capture uses only bundled resources and synthetic inputs, never user data or
network access. Whole Swing view images are review artifacts rather than portable
font-rasterization assertions; see the view notes for their automated checks.
