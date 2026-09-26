# Ship Artwork surface baselines

These nine PNGs characterize the current artwork in real production Swing
components before the Ship Artwork migration. They were captured with JDK 25,
the cross-platform Metal look and feel, and headless component painting on
Windows. Artwork is 64 × 64 pixels in each surface. The synthetic `Cruiser`
(Federation, Engineering, Epic) and `Dhelan Warbird` (Romulan, Science, Very Rare)
use the existing bundled `Cruiser.png` and `Dhelan_Warbird.png` assets. Both have
Tier 6 and 50/50/50 statistics for this fixture; these are not assertions about
actual game data.

| PNG | Production surface | Current artwork |
| --- | --- | --- |
| `reusable-roster.png` | `ShipFilterViews.reusableRoster` | Specific images for both reusable cards |
| `one-time-ships.png` | `ShipFilterViews.oneTimeRoster` | Generic Cruiser with `(1x)` display name |
| `roster-starship-traits.png` | `ShipFilterViews.rosterStarshipTraits` | Specific Cruiser image |
| `gamedata-starship-traits.png` | `ShipFilterViews.gameDataStarshipTraits` | Generic Cruiser image |
| `reusable-selection.png` | `reusableShipSelection` in a `JOptionPane` | Generic candidates and generic selected-Ship details |
| `one-time-selection.png` | `oneTimeShipSelection` in a `JOptionPane` | Generic candidates and generic selected-Ship details |
| `roster-card-selection.png` | `rosterCardSelection` in a `JOptionPane` | Specific reusable-card images |
| `ship-usage.png` | `ShipFilterViews.shipUsage` | Specific current-Roster Cruiser; generic historical-only Dhelan |
| `solution-cards.png` | `AssignmentPanel.setAssignmentSolution` | Actual solved reusable Cruiser/Dhelan cards and a generic One-Time Cruiser |

The first eight images are 1000 × 400; the Solution is 1000 × 620. Selection
captures use the real dialog content and option labels, with the first canonical
candidate (Dhelan) selected. Native window borders, dialog modality, input events,
and surrounding Admiral workspace tabs are outside these headless baselines.
The two Ship candidate dialogs intentionally look alike for this Tier 6 fixture.
Trait text retains the production Metal appearance, including its dark foreground.
No theme or production renderer behavior was changed to improve the captures.

`ShipArtworkVisualBaseline` builds fresh canonical GameData and an empty Icon
Cache, never loads or saves that cache, never initializes global application
state, and does not access the network. All Swing work runs on the event thread.
Temporary lightweight peers allow Swing's own renderer validation to lay out
complete list cards without a native window.

Generate review images without replacing these approved baselines:

```powershell
.\gradlew.bat shipArtworkVisualBaseline
```

Review output in `build/ship-artwork-views/`. To deliberately replace the
checked-in captures after reviewing an intentional presentation change:

```powershell
.\gradlew.bat shipArtworkVisualBaseline --args="test/resources/ship-artwork/views"
```

Run the portable surface characterization:

```powershell
.\gradlew.bat test --tests com.kor.admiralty.ui.ShipArtworkViewsTest
```

The test verifies all nine artifact names and dimensions, paints the actual
components, and checks exact artwork pixels from every list and Solution card
against the expected generic or specific factory result. It also verifies the
selection details artwork and that the fixture distinguishes generic from
specific pixels. It compares artwork multisets because slot/canonical ordering
is outside this characterization. It deliberately does not compare full UI
pixels: fonts, look and feel, and host rendering can change those pixels without
changing Ship Artwork. The adjacent composition baselines independently retain
the factory pixels so a later factory change is not accepted merely because the
surface and expected factory changed together.

All nine retained PNGs were visually inspected after generation: artwork is
fully visible, card text and selection details are laid out, and reusable versus
One-Time presentation is distinguishable in the same Solution.
