# Passive Roster Ship Filter comparison

These captures verify the passive Roster migration in issue #42. The existing
`ShipFilterVisualBaseline` modes render the real `AdmiralPanel`, so they exercise
the migrated Active, Maintenance, One-Time, and Roster Starship Trait views
without changing the visual fixture. The window titles retain the harness's
established `Ship Filter Before` text to allow exact image comparison.

| Presentation | Original baseline | Migrated capture |
| --- | --- | --- |
| Active and Maintenance Roster | [Before](../ship-filter-before/primary-roster.png) | [After](primary-roster.png) |
| One-Time Roster | [Before](../ship-filter-before/one-time-roster.png) | [After](one-time-roster.png) |
| Roster Starship Traits | [Before](../ship-filter-before/roster-traits.png) | [After](roster-traits.png) |

On Java 25 at Windows desktop scale 100%, all three migrated images were
1100 by 760 pixels and matched fresh captures from pre-migration commit
`582c72222fef2ae4cf5ecfb4f51c077216164c9e` with **zero differing ARGB pixels**.
Visual inspection also confirmed the established list layout, artwork, labels,
counts, duplicate One-Time cards, action controls, and Starship Trait content.

Compared with the older checked-in baselines, each fresh pre-migration capture
differs only in the 16 by 16 native Java window icon at coordinates
`(10, 11)` through `(25, 26)`. Application content is identical. Comparing
fresh before and after captures on the same runtime avoids that environmental
window-icon difference.

## Reproducing the migrated captures

Compile the test utilities and assemble their dependency classpath as described
in the [original baseline instructions](../ship-filter-before/README.md).
Capture into `target/` when verifying locally to preserve the checked-in images:

```powershell
$classpath = "target/test-classes;target/classes;$(Get-Content -Raw target/visual-classpath.txt)"
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline primary-roster `
  target/ship-filter-after-primary-roster.png
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline one-time-roster `
  target/ship-filter-after-one-time-roster.png
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline roster-traits `
  target/ship-filter-after-roster-traits.png
```
