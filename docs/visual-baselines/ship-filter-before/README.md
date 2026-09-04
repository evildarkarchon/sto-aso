# Ship Filter before-migration visual baseline

These screenshots record the established Swing presentation before the Ship
Filter migration tracked by GitHub issue #37. They were captured for issue #38
from the small `test/resources/gamedata` fixture with the deterministic
`ShipFilterVisualBaseline` tool on Java 25 at Windows desktop scale 100%. The
tool renders each real visible Swing window through `printAll`, which keeps the
capture reproducible when GUI automation and the launched JVM use separate
Windows stations.

| View | Baseline |
| --- | --- |
| Reusable Ship selection | [active-dialog.png](active-dialog.png) |
| One-Time Ship selection | [one-time-dialog.png](one-time-dialog.png) |
| Roster-card selection | [roster-card-dialog.png](roster-card-dialog.png) |
| Active and Maintenance Roster | [primary-roster.png](primary-roster.png) |
| One-Time Roster | [one-time-roster.png](one-time-roster.png) |
| Roster Starship Traits | [roster-traits.png](roster-traits.png) |
| GameData Starship Traits | [game-data-traits.png](game-data-traits.png) |
| Ship usage (initial Most Used order) | [ship-usage.png](ship-usage.png) |

The three dialog captures intentionally expand the filter pane. The two Ship
dialogs select the first visible Ship so the details column is populated; the
Roster-card dialog demonstrates the compact list-only layout.

## Reproducing the views

Compile test utilities and assemble their dependency classpath:

```powershell
mvn -DskipTests test-compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/visual-classpath.txt"
```

Then launch one view interactively, or provide a PNG path to capture it:

```powershell
$classpath = "target/test-classes;target/classes;$(Get-Content -Raw target/visual-classpath.txt)"
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline active-dialog
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline active-dialog `
  docs/visual-baselines/ship-filter-before/active-dialog.png
```

Valid view names are `active-dialog`, `active-dialog-after`,
`one-time-dialog`, `one-time-dialog-after`, `roster-card-dialog`,
`roster-card-dialog-after`, `primary-roster`, `one-time-roster`,
`roster-traits`, `game-data-traits`, and `ship-usage`.

## Reusable Ship selection comparison

Issue #40 preserves `active-dialog` as the reproducible pre-migration view and
adds `active-dialog-after` for the named Ship Filter presentation. Capture the
migrated view outside this baseline directory so the checked-in reference is
never overwritten:

```powershell
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline active-dialog-after `
  target/ship-filter-after-active.png
```

On Java 25 at Windows desktop scale 100%, the issue #40 capture was compared
pixel-for-pixel with `active-dialog.png`: both images were 683 by 1121 pixels,
with zero differing RGB channel samples.

## Remaining selection dialog comparisons

Issue #41 preserves `one-time-dialog` and `roster-card-dialog` as the
reproducible pre-migration views. Their named Ship Filter counterparts use the
`-after` suffix and should be captured outside this baseline directory:

```powershell
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline one-time-dialog-after `
  target/ship-filter-after-one-time.png
java -cp $classpath com.kor.admiralty.ui.ShipFilterVisualBaseline roster-card-dialog-after `
  target/ship-filter-after-roster-card.png
```

On Java 25 at Windows desktop scale 100%, the issue #41 captures were compared
pixel-for-pixel with their checked-in baselines. One-Time Ship selection was 683
by 1121 pixels and RosterCard selection was 423 by 1121 pixels; both comparisons
had zero differing RGB channel samples.
