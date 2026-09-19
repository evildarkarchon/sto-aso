# Legacy Ship Artwork archives

These small, offline fixtures freeze the pre-migration `icons.zip` format. They
were captured on 2026-09-18 with Temurin JDK 25.0.4.1 using the current
`ActualShipIconFactory` and repository-bundled PNG resources. They contain no
downloaded images or user data. Tests copy each archive to a temporary directory
as `icons.zip` before calling the current loader.

| Archive | Entries in archive order | Purpose |
| --- | --- | --- |
| `recognizable.zip` | `Class_F_Shuttle.png`, `Brel_Bird_of_Prey.png` | Two current canonical Ship filenames and their original composed pixels |
| `recognizable-and-unmatched.zip` | `Class_F_Shuttle.png`, `Retired_Prototype.png` | A canonical entry plus a readable image with no current Ship mapping |
| `recognizable-and-unreadable.zip` | `Class_F_Shuttle.png`, `Broken_Artwork.png` | A valid entry before an unreadable entry, exposing whole-archive recovery |

The recognizable entries map to `Class F Shuttle` (Federation, Small Craft,
Common) and `B'rel Bird-of-Prey` (Klingon, Tactical, Common) in `data/ships.csv`.
Their source resources are `Class_F_Shuttle.png` and `Brel_Bird_of_Prey.png` under
`src/com/kor/admiralty/ui/resources`. Each was requested with `owned=true` from an
empty temporary `IconCache`. The resulting 64 by 64 ARGB image contains faction
background, smoothly scaled bundled source image, role frame, then rarity frame;
Common adds no rarity frame. These are already composed images, not raw source
images, and later migration must preserve that distinction.

`Retired_Prototype.png` is a synthetic 64 by 64 ARGB checkerboard: each 8 by 8
tile alternates `0xffcc33aa` and `0x804499cc`, with the opaque tile at the origin.
It deliberately has no current canonical Ship. `Broken_Artwork.png` contains
the UTF-8 bytes `Deliberately not a PNG.` followed by LF, despite its suffix.

All archives use STORED ZIP entries, fixed local DOS timestamp
`2026-01-01T00:00:00`, and the order shown above. PNG bytes were encoded with
JDK `ImageIO.write`. Freeze these fixture bytes when changing the production
implementation; do not regenerate them from the new renderer.

## Current loader observations

`LegacyArtworkArchiveTest` records exact-key lookup, 64 by 64 dimensions, every
decoded ARGB pixel, preservation of readable unmatched entries, and no rewrite
after a successful load. The current loader has no canonical identity mapping.
One unreadable image clears both earlier decoded entries and previous in-memory
contents, deletes the temporary archive copy, and marks the cache stale. These
are characterization observations, not the desired migration contract: later
migration retains the original legacy archive and salvages recognizable pixels.
